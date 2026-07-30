package com.medisalud.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.repository.MedicoRepository;
import com.medisalud.agenda.repository.PacienteRepository;
import com.medisalud.agenda.repository.PenalizacionPacienteRepository;
import com.medisalud.agenda.support.ConfiguracionDeRelojDePruebas;
import com.medisalud.agenda.support.RelojAjustable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
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
 * Recorrido completo del sistema por HTTP, en el orden en que lo usaria una recepcion real:
 * registrar medico, registrar paciente, consultar disponibilidad, agendar, chocar con las
 * reglas de solapamiento, cancelar tarde y comprobar la penalizacion.
 *
 * <p>Es el test que responde a "esto funciona de verdad". Los demas verifican reglas
 * aisladas; este comprueba que encajan entre si: que la franja que ofrece la
 * disponibilidad es agendable, que la que se agenda desaparece de la disponibilidad, que
 * las dos reglas de solapamiento distinguen bien de quien es el conflicto, y que cancelar
 * devuelve la franja al listado y deja rastro en las penalizaciones.</p>
 *
 * <p>Sin datos precargados: el perfil {@code test} desactiva la carga inicial de medicos,
 * asi que todo lo que aparece aqui lo crea el propio test a traves de la API.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConfiguracionDeRelojDePruebas.class)
@ActiveProfiles("test")
class FlujoCompletoTest {

    /** Lunes 3 de agosto de 2026 a las 07:00, antes de abrir: el dia entero por delante. */
    private static final LocalDateTime LUNES_07_00 =
            ConfiguracionDeRelojDePruebas.MOMENTO_INICIAL.withHour(7);

    private static final String LUNES = "2026-08-03";
    private static final LocalDateTime CITA = LocalDateTime.of(2026, 8, 3, 15, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private RelojAjustable reloj;
    @Autowired private MedicoRepository medicoRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private PenalizacionPacienteRepository penalizacionRepository;

    @BeforeEach
    void limpiarYSituarElReloj() {
        penalizacionRepository.deleteAll();
        citaRepository.deleteAll();
        pacienteRepository.deleteAll();
        medicoRepository.deleteAll();

        reloj.situarEn(LUNES_07_00);
    }

    @Test
    @DisplayName("Registrar, agendar, chocar con las reglas, cancelar tarde y quedar penalizado")
    void recorridoCompleto() throws Exception {
        // ---------------------------------------------------- 1. Registrar médicos
        long cardiologa = crear("/api/medicos", """
                {"nombreCompleto":"Dra. María González","especialidad":"Cardiología",
                 "telefono":"555-1001","email":"maria.gonzalez@medisalud.com"}""");
        long pediatra = crear("/api/medicos", """
                {"nombreCompleto":"Dr. Carlos Ruiz","especialidad":"Pediatría",
                 "telefono":"555-1002","email":"carlos.ruiz@medisalud.com"}""");

        mockMvc.perform(get("/api/medicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Ordenados por nombre: "Dr. Carlos" antes que "Dra. María".
                .andExpect(jsonPath("$[0].nombreCompleto").value("Dr. Carlos Ruiz"));

        // -------------------------------------------------- 2. Registrar pacientes
        long juan = crear("/api/pacientes", """
                {"nombreCompleto":"Juan Pérez","documentoIdentidad":"1020304050",
                 "telefono":"3001234567","email":"juan.perez@example.com",
                 "fechaNacimiento":"1990-05-20"}""");
        long ana = crear("/api/pacientes", """
                {"nombreCompleto":"Ana Torres","documentoIdentidad":"9988776655",
                 "telefono":"3009998888","email":"ana.torres@example.com"}""");

        mockMvc.perform(get("/api/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombreCompleto").value("Ana Torres"));

        // Consulta individual de cada recurso, y 404 cuando no existe.
        mockMvc.perform(get("/api/medicos/{id}", cardiologa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidad").value("Cardiología"))
                .andExpect(jsonPath("$.email").value("maria.gonzalez@medisalud.com"));
        mockMvc.perform(get("/api/pacientes/{id}", juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentoIdentidad").value("1020304050"))
                .andExpect(jsonPath("$.fechaNacimiento").value("1990-05-20"));
        mockMvc.perform(get("/api/medicos/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontró el médico con id 9999."));
        mockMvc.perform(get("/api/pacientes/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontró el paciente con id 9999."));

        // El documento es único: repetirlo choca con un 409, no con un 500.
        mockMvc.perform(post("/api/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreCompleto":"Otro Juan","documentoIdentidad":"1020304050",
                                 "telefono":"3005554444","email":"otro@example.com"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_DUPLICADO"));

        // ------------------------------------------------ 3. Consultar disponibilidad
        mockMvc.perform(get("/api/medicos/{id}/disponibilidad", cardiologa)
                        .param("fechaInicio", LUNES).param("fechaFin", LUNES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFranjasDisponibles").value(20))
                .andExpect(jsonPath("$.dias[0].franjas", Matchers.hasItem("15:00:00")));

        // -------------------------------------------------------- 4. Agendar la cita
        long citaId = reservar(cardiologa, juan, CITA);

        // La franja agendada ya no se ofrece.
        mockMvc.perform(get("/api/medicos/{id}/disponibilidad", cardiologa)
                        .param("fechaInicio", LUNES).param("fechaFin", LUNES))
                .andExpect(jsonPath("$.totalFranjasDisponibles").value(19))
                .andExpect(jsonPath("$.dias[0].franjas", Matchers.not(Matchers.hasItem("15:00:00"))));

        // ------------------------------------- 5. Intentar duplicar: RN-02 y RN-04
        // Misma franja y mismo médico, con otro paciente.
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(cardiologa, ana, CITA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MEDICO_NO_DISPONIBLE"));

        // Misma franja y mismo paciente, con otro médico: una persona no está en dos sitios.
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(pediatra, juan, CITA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PACIENTE_NO_DISPONIBLE"));

        // El pediatra sí puede atender esa franja a otra paciente.
        reservar(pediatra, ana, CITA);

        // ------------------------------------------ 6. Cancelar tarde: RF-05 y RN-05
        reloj.situarEn(CITA.minusHours(1));

        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.penalizacion.registrada").value(true))
                .andExpect(jsonPath("$.penalizacion.motivo")
                        .value("Cancelación con 1 h 0 min de antelación."))
                .andExpect(jsonPath("$.penalizacion.totalVigentes").value(1))
                // Una sola penalización no bloquea: hacen falta tres.
                .andExpect(jsonPath("$.penalizacion.pacienteBloqueado").value(false));

        // ------------------------------------------------ 7. Verificar la penalización
        assertThat(penalizacionRepository.count()).isEqualTo(1);
        assertThat(penalizacionRepository.findAll().getFirst())
                .satisfies(penalizacion -> {
                    assertThat(penalizacion.getPaciente().getId()).isEqualTo(juan);
                    assertThat(penalizacion.getCita().getId()).isEqualTo(citaId);
                    assertThat(penalizacion.getFechaRegistro()).isEqualTo(CITA.minusHours(1));
                });

        // El listado refleja los dos estados.
        mockMvc.perform(get("/api/citas").param("estado", "CANCELADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value((int) citaId));
        mockMvc.perform(get("/api/citas").param("pacienteId", String.valueOf(juan)))
                .andExpect(jsonPath("$.length()").value(1));

        // Y la franja vuelve a estar libre para el médico que la tenía ocupada (RN-02).
        mockMvc.perform(get("/api/medicos/{id}/disponibilidad", cardiologa)
                        .param("fechaInicio", LUNES).param("fechaFin", LUNES))
                .andExpect(jsonPath("$.dias[0].franjas", Matchers.hasItem("15:00:00")));
    }

    // ------------------------------------------------------------------ apoyo

    private long reservar(long medico, long paciente, LocalDateTime fechaHora) throws Exception {
        String respuesta = mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCita(medico, paciente, fechaHora)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Integer>read(respuesta, "$.id").longValue();
    }

    private String cuerpoDeCita(long medico, long paciente, LocalDateTime fechaHora) {
        return """
                {"medicoId": %d, "pacienteId": %d, "fechaHora": "%s"}"""
                .formatted(medico, paciente, fechaHora);
    }

    private long crear(String ruta, String cuerpo) throws Exception {
        String respuesta = mockMvc.perform(post(ruta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.containsString(ruta)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Integer>read(respuesta, "$.id").longValue();
    }
}
