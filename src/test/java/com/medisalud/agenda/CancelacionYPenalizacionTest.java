package com.medisalud.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.repository.MedicoRepository;
import com.medisalud.agenda.repository.PacienteRepository;
import com.medisalud.agenda.repository.PenalizacionPacienteRepository;
import com.medisalud.agenda.support.RelojAjustable;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ciclo completo de la RN-05 de extremo a extremo: cancelar tarde, acumular
 * penalizaciones, quedar bloqueado y recuperar el acceso cuando la ventana expira.
 *
 * <p>Es un test de integracion real: contexto completo, base de datos y peticiones HTTP a
 * traves de {@code MockMvc}. Lo unico sustituido es el reloj, por un
 * {@link RelojAjustable} que el test mueve a voluntad. Sin eso, comprobar el desbloqueo a
 * los 30 dias exigiria falsificar filas en la base de datos, que es justo lo que este test
 * deberia detectar si estuviera mal.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancelacionYPenalizacionTest {

    private static final LocalDateTime LUNES_08_00 = LocalDateTime.of(2026, 8, 3, 8, 0);

    @TestConfiguration
    static class RelojDePruebas {

        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable(LUNES_08_00, ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RelojAjustable reloj;
    @Autowired private MedicoRepository medicoRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private PenalizacionPacienteRepository penalizacionRepository;

    private long medicoId;
    private long pacienteId;

    @BeforeEach
    void prepararEscenario() throws Exception {
        penalizacionRepository.deleteAll();
        citaRepository.deleteAll();
        pacienteRepository.deleteAll();
        medicoRepository.deleteAll();

        reloj.situarEn(LUNES_08_00);

        medicoId = crear("/api/medicos", """
                {"nombreCompleto":"Dra. María González","especialidad":"Cardiología",
                 "telefono":"555-1001","email":"maria.gonzalez@medisalud.com"}""");

        pacienteId = crear("/api/pacientes", """
                {"nombreCompleto":"Juan Pérez","documentoIdentidad":"1020304050",
                 "telefono":"3001234567","email":"juan.perez@example.com"}""");
    }

    @Test
    @DisplayName("Cancelar con más de 2 horas de antelación no penaliza y libera la franja")
    void cancelacionATiempo() throws Exception {
        long citaId = reservar(LUNES_08_00.withHour(15));

        // 07:00 horas antes de la cita: muy por encima del umbral de 2 horas.
        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.penalizacionRegistrada").value(false))
                .andExpect(jsonPath("$.penalizacionesVigentes").value(0))
                .andExpect(jsonPath("$.pacienteBloqueado").value(false));

        assertThat(penalizacionRepository.count()).isZero();

        // RN-02: la cancelación devuelve la franja al mercado.
        reservar(LUNES_08_00.withHour(15));
    }

    @Test
    @DisplayName("Cancelar una cita ya cancelada devuelve 409")
    void cancelacionRepetida() throws Exception {
        long citaId = reservar(LUNES_08_00.withHour(15));
        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CITA_NO_CANCELABLE"));
    }

    @Test
    @DisplayName("Tres cancelaciones tardías bloquean al paciente y el bloqueo cae al expirar la ventana")
    void bloqueoYDesbloqueo() throws Exception {
        // --- Dos cancelaciones tardías: penalizan, pero todavía no bloquean ---------
        cancelarTardeUnaCitaEl(LUNES_08_00);
        cancelarTardeUnaCitaEl(LUNES_08_00.plusDays(1));

        reloj.situarEn(LUNES_08_00.plusDays(1).withHour(11));
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(LUNES_08_00.plusDays(1).withHour(16))))
                .andExpect(status().isCreated());

        // --- La tercera cierra la puerta -------------------------------------------
        String respuesta = cancelarTardeUnaCitaEl(LUNES_08_00.plusDays(2));

        assertThat(JsonPath.<Boolean>read(respuesta, "$.pacienteBloqueado")).isTrue();
        assertThat(JsonPath.<Integer>read(respuesta, "$.penalizacionesVigentes")).isEqualTo(3);
        // La primera penalización se registró el 03/08 a las 09:30; expira 30 días después.
        assertThat(JsonPath.<String>read(respuesta, "$.puedeAgendarDesde"))
                .isEqualTo("2026-09-02T09:30:00");

        // --- Bloqueado: no puede agendar, y se le dice hasta cuándo ----------------
        reloj.situarEn(LUNES_08_00.plusDays(3));
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(LUNES_08_00.plusDays(3).withHour(11))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PACIENTE_BLOQUEADO"))
                .andExpect(jsonPath("$.mensaje").value(
                        "El paciente acumula 3 cancelaciones tardías en los últimos 30 días "
                                + "y no puede agendar nuevas citas hasta el 02/09/2026 a las 09:30."));

        // --- Un minuto antes de que expire, sigue bloqueado ------------------------
        reloj.situarEn(LocalDateTime.of(2026, 9, 2, 9, 29));
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(LocalDateTime.of(2026, 9, 2, 11, 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PACIENTE_BLOQUEADO"));

        // --- Un minuto después, la más antigua ya no cuenta y vuelve a agendar -----
        reloj.situarEn(LocalDateTime.of(2026, 9, 2, 9, 31));
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(LocalDateTime.of(2026, 9, 2, 11, 0))))
                .andExpect(status().isCreated());

        // Las tres penalizaciones siguen en la tabla: expiran, no se borran.
        assertThat(penalizacionRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Cancelar después de la hora de la cita también penaliza")
    void cancelacionPosteriorALaCita() throws Exception {
        long citaId = reservar(LUNES_08_00.withHour(10));

        reloj.situarEn(LUNES_08_00.withHour(12));

        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalizacionRegistrada").value(true))
                .andExpect(jsonPath("$.motivoPenalizacion")
                        .value("Cancelación posterior a la hora de la cita."));
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * Reserva una cita a las 10:00 del dia indicado y la cancela a las 09:30, es decir con
     * media hora de antelacion. Devuelve el cuerpo de la respuesta de cancelacion.
     */
    private String cancelarTardeUnaCitaEl(LocalDateTime dia) throws Exception {
        reloj.situarEn(dia.withHour(8).withMinute(0));
        long citaId = reservar(dia.withHour(10).withMinute(0));

        reloj.situarEn(dia.withHour(9).withMinute(30));
        return mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalizacionRegistrada").value(true))
                .andExpect(jsonPath("$.motivoPenalizacion")
                        .value("Cancelación con 30 min de antelación."))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long reservar(LocalDateTime fechaHora) throws Exception {
        String respuesta = mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(fechaHora)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Integer>read(respuesta, "$.id").longValue();
    }

    private String cuerpoDeCita(LocalDateTime fechaHora) {
        return """
                {"medicoId": %d, "pacienteId": %d, "fechaHora": "%s"}"""
                .formatted(medicoId, pacienteId, fechaHora);
    }

    private long crear(String ruta, String cuerpo) throws Exception {
        String respuesta = mockMvc.perform(post(ruta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Integer>read(respuesta, "$.id").longValue();
    }
}
