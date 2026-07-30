package com.medisalud.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.repository.MedicoRepository;
import com.medisalud.agenda.repository.PacienteRepository;
import com.medisalud.agenda.repository.PenalizacionPacienteRepository;
import com.medisalud.agenda.support.ConfiguracionDeRelojDePruebas;
import com.medisalud.agenda.support.RelojAjustable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reprogramacion de extremo a extremo (RN-06).
 *
 * <p>Lo que de verdad se comprueba aqui es la atomicidad: que una reprogramacion rechazada
 * no deje al paciente sin la cita que tenia. Eso solo se puede verificar contra una base de
 * datos real, porque depende de que la transaccion revierta la cancelacion ya aplicada en
 * memoria.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConfiguracionDeRelojDePruebas.class)
@ActiveProfiles("test")
class ReprogramacionTest {

    private static final LocalDateTime LUNES_08_00 = ConfiguracionDeRelojDePruebas.MOMENTO_INICIAL;

    @Autowired private MockMvc mockMvc;
    @Autowired private RelojAjustable reloj;
    @Autowired private MedicoRepository medicoRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private PenalizacionPacienteRepository penalizacionRepository;

    private long medicoId;
    private long pacienteId;
    private long otroPacienteId;

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

        otroPacienteId = crear("/api/pacientes", """
                {"nombreCompleto":"Ana Torres","documentoIdentidad":"9988776655",
                 "telefono":"3009998888","email":"ana.torres@example.com"}""");
    }

    @Test
    @DisplayName("Reprogramar cierra la cita anterior, abre la nueva y las enlaza")
    void reprogramacionExitosa() throws Exception {
        long citaId = reservar(pacienteId, LUNES_08_00.withHour(15));

        String respuesta = mockMvc.perform(patch("/api/citas/{id}/reprogramar", citaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoHorario(LUNES_08_00.withHour(16))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citaAnterior.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.citaNueva.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.citaNueva.fechaHora").value("2026-08-03T16:00:00"))
                .andExpect(jsonPath("$.citaNueva.citaOrigenId").value((int) citaId))
                .andExpect(jsonPath("$.penalizacion.registrada").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        long nuevaId = JsonPath.<Integer>read(respuesta, "$.citaNueva.id").longValue();
        assertThat(nuevaId).isNotEqualTo(citaId);

        // Quedan las dos citas: reprogramar no borra, cierra una y abre otra.
        assertThat(citaRepository.count()).isEqualTo(2);
        assertThat(citaRepository.findById(citaId)).get()
                .satisfies(anterior -> {
                    assertThat(anterior.getEstado()).isEqualTo(EstadoCita.CANCELADA);
                    assertThat(anterior.getFechaCancelacion()).isEqualTo(LUNES_08_00);
                });

        // La franja liberada vuelve a estar disponible para otro paciente (RN-02).
        reservar(otroPacienteId, LUNES_08_00.withHour(15));
    }

    @Test
    @DisplayName("Si el nuevo horario está ocupado, la cita original se conserva intacta")
    void conflictoNoDejaEstadoInconsistente() throws Exception {
        long citaId = reservar(pacienteId, LUNES_08_00.withHour(15));
        // El mismo médico ya tiene ocupada la franja de destino con otro paciente.
        reservar(otroPacienteId, LUNES_08_00.withHour(16));

        mockMvc.perform(patch("/api/citas/{id}/reprogramar", citaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoHorario(LUNES_08_00.withHour(16))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MEDICO_NO_DISPONIBLE"));

        // Lo importante: el paciente NO se ha quedado sin cita.
        mockMvc.perform(get("/api/citas/{id}", citaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.fechaHora").value("2026-08-03T15:00:00"))
                .andExpect(jsonPath("$.fechaCancelacion").doesNotExist());

        assertThat(citaRepository.count()).isEqualTo(2);
        assertThat(penalizacionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Reprogramar con menos de 2 horas de antelación también penaliza")
    void reprogramarTardePenaliza() throws Exception {
        long citaId = reservar(pacienteId, LUNES_08_00.withHour(10));

        // 09:30: media hora antes de la cita original.
        reloj.situarEn(LUNES_08_00.withHour(9).withMinute(30));

        mockMvc.perform(patch("/api/citas/{id}/reprogramar", citaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoHorario(LUNES_08_00.withHour(16))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citaNueva.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.penalizacion.registrada").value(true))
                .andExpect(jsonPath("$.penalizacion.motivo")
                        .value("Cancelación con 30 min de antelación."))
                .andExpect(jsonPath("$.penalizacion.totalVigentes").value(1))
                .andExpect(jsonPath("$.penalizacion.pacienteBloqueado").value(false));

        assertThat(penalizacionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reprogramar al horario que la cita ya tenía devuelve 400 con su propio mensaje")
    void mismoHorarioSeRechaza() throws Exception {
        long citaId = reservar(pacienteId, LUNES_08_00.withHour(15));

        mockMvc.perform(patch("/api/citas/{id}/reprogramar", citaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoHorario(LUNES_08_00.withHour(15))))
                .andExpect(status().isBadRequest())
                // Sin esta comprobación explícita, la cita chocaría consigo misma y el
                // mensaje diría que el médico ya tiene una cita ahí, que es la suya.
                .andExpect(jsonPath("$.codigo").value("REPROGRAMACION_SIN_CAMBIO"))
                .andExpect(jsonPath("$.mensaje").value("La cita ya está programada para esa fecha y hora."));

        assertThat(citaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una cita ya cancelada no se puede reprogramar")
    void citaCanceladaNoSeReprograma() throws Exception {
        long citaId = reservar(pacienteId, LUNES_08_00.withHour(15));
        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/citas/{id}/reprogramar", citaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoHorario(LUNES_08_00.withHour(16))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CITA_NO_MODIFICABLE"));
    }

    @Test
    @DisplayName("Un paciente bloqueado no puede agendar, pero sí reprogramar la cita que ya tenía")
    void pacienteBloqueadoPuedeReprogramar() throws Exception {
        // Cita futura que el paciente ya tiene antes de meterse en problemas.
        long citaFutura = reservar(pacienteId, LocalDateTime.of(2026, 9, 10, 10, 0));

        cancelarTardeUnaCitaEl(LUNES_08_00);
        cancelarTardeUnaCitaEl(LUNES_08_00.plusDays(1));
        cancelarTardeUnaCitaEl(LUNES_08_00.plusDays(2));

        reloj.situarEn(LUNES_08_00.plusDays(2).withHour(11));

        // Agendar una cita nueva le está vetado...
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(pacienteId, LUNES_08_00.plusDays(2).withHour(16))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PACIENTE_BLOQUEADO"));

        // ...pero mover la que ya tenía, no. Bloquearlo aquí solo le dejaría no
        // presentarse o cancelar, y cancelar le sumaría otra penalización.
        mockMvc.perform(patch("/api/citas/{id}/reprogramar", citaFutura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoHorario(LocalDateTime.of(2026, 9, 10, 11, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citaNueva.fechaHora").value("2026-09-10T11:00:00"))
                .andExpect(jsonPath("$.penalizacion.pacienteBloqueado").value(true));
    }

    // ------------------------------------------------------------------ apoyo

    /** Reserva a las 10:00 del dia indicado y cancela a las 09:30: penaliza. */
    private void cancelarTardeUnaCitaEl(LocalDateTime dia) throws Exception {
        reloj.situarEn(dia.withHour(8).withMinute(0));
        long citaId = reservar(pacienteId, dia.withHour(10).withMinute(0));

        reloj.situarEn(dia.withHour(9).withMinute(30));
        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalizacion.registrada").value(true));
    }

    private long reservar(long paciente, LocalDateTime fechaHora) throws Exception {
        String respuesta = mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(paciente, fechaHora)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Integer>read(respuesta, "$.id").longValue();
    }

    private String cuerpoDeCita(long paciente, LocalDateTime fechaHora) {
        return """
                {"medicoId": %d, "pacienteId": %d, "fechaHora": "%s"}"""
                .formatted(medicoId, paciente, fechaHora);
    }

    private String nuevoHorario(LocalDateTime fechaHora) {
        return """
                {"nuevaFechaHora": "%s"}""".formatted(fechaHora);
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
