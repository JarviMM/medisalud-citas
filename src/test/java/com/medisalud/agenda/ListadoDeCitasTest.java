package com.medisalud.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.repository.MedicoRepository;
import com.medisalud.agenda.repository.PacienteRepository;
import com.medisalud.agenda.repository.PenalizacionPacienteRepository;
import com.medisalud.agenda.support.ConfiguracionDeRelojDePruebas;
import com.medisalud.agenda.support.RelojAjustable;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Listado de citas con filtros combinables (RF-06).
 *
 * <p>Se prueba contra la base de datos real y no con dobles porque lo que hay que verificar
 * es la traduccion de los filtros a SQL, que es justo lo que un doble ocultaria.</p>
 *
 * <p>El escenario fijo es: dos medicos, dos pacientes y cuatro citas repartidas en dos dias,
 * una de ellas cancelada. Con eso, cada filtro devuelve un subconjunto distinto y ninguno
 * puede pasar por casualidad.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConfiguracionDeRelojDePruebas.class)
@ActiveProfiles("test")
class ListadoDeCitasTest {

    private static final LocalDateTime LUNES_08_00 = ConfiguracionDeRelojDePruebas.MOMENTO_INICIAL;
    private static final String LUNES = "2026-08-03";
    private static final String MARTES = "2026-08-04";

    @Autowired private MockMvc mockMvc;
    @Autowired private RelojAjustable reloj;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private MedicoRepository medicoRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private PenalizacionPacienteRepository penalizacionRepository;

    private long cardiologa;
    private long pediatra;
    private long juan;
    private long ana;

    @BeforeEach
    void prepararEscenario() throws Exception {
        penalizacionRepository.deleteAll();
        citaRepository.deleteAll();
        pacienteRepository.deleteAll();
        medicoRepository.deleteAll();

        reloj.situarEn(LUNES_08_00);

        cardiologa = crear("/api/medicos", """
                {"nombreCompleto":"Dra. María González","especialidad":"Cardiología",
                 "telefono":"555-1001","email":"maria.gonzalez@medisalud.com"}""");
        pediatra = crear("/api/medicos", """
                {"nombreCompleto":"Dr. Carlos Ruiz","especialidad":"Pediatría",
                 "telefono":"555-1002","email":"carlos.ruiz@medisalud.com"}""");

        juan = crear("/api/pacientes", """
                {"nombreCompleto":"Juan Pérez","documentoIdentidad":"1020304050",
                 "telefono":"3001234567","email":"juan.perez@example.com"}""");
        ana = crear("/api/pacientes", """
                {"nombreCompleto":"Ana Torres","documentoIdentidad":"9988776655",
                 "telefono":"3009998888","email":"ana.torres@example.com"}""");

        // Lunes: cardióloga con Juan a las 09:00 y con Ana a las 10:00.
        reservar(cardiologa, juan, LUNES_08_00.withHour(9));
        long citaDeAna = reservar(cardiologa, ana, LUNES_08_00.withHour(10));
        // Martes: pediatra con Juan a las 09:00 y cardióloga con Juan a las 11:00.
        reservar(pediatra, juan, LUNES_08_00.plusDays(1).withHour(9));
        reservar(cardiologa, juan, LUNES_08_00.plusDays(1).withHour(11));

        // Una cancelada, para poder filtrar por estado con sentido.
        mockMvc.perform(patch("/api/citas/{id}/cancelar", citaDeAna)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin filtros devuelve todas las citas, en orden cronológico")
    void sinFiltros() throws Exception {
        mockMvc.perform(get("/api/citas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].fechaHora").value("2026-08-03T09:00:00"))
                .andExpect(jsonPath("$[1].fechaHora").value("2026-08-03T10:00:00"))
                .andExpect(jsonPath("$[2].fechaHora").value("2026-08-04T09:00:00"))
                .andExpect(jsonPath("$[3].fechaHora").value("2026-08-04T11:00:00"));
    }

    @Nested
    @DisplayName("Filtros individuales")
    class FiltrosIndividuales {

        @Test
        @DisplayName("Por médico")
        void porMedico() throws Exception {
            mockMvc.perform(get("/api/citas").param("medicoId", String.valueOf(pediatra)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].medico.especialidad").value("Pediatría"));
        }

        @Test
        @DisplayName("Por paciente")
        void porPaciente() throws Exception {
            mockMvc.perform(get("/api/citas").param("pacienteId", String.valueOf(ana)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].paciente.nombreCompleto").value("Ana Torres"));
        }

        @Test
        @DisplayName("Por estado")
        void porEstado() throws Exception {
            mockMvc.perform(get("/api/citas").param("estado", "CANCELADA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].estado").value("CANCELADA"));

            mockMvc.perform(get("/api/citas").param("estado", "PROGRAMADA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3));
        }

        @Test
        @DisplayName("Por rango de fechas, con ambos extremos incluidos")
        void porRangoDeFechas() throws Exception {
            mockMvc.perform(get("/api/citas")
                            .param("fechaInicio", LUNES)
                            .param("fechaFin", LUNES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            mockMvc.perform(get("/api/citas")
                            .param("fechaInicio", LUNES)
                            .param("fechaFin", MARTES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4));
        }

        @Test
        @DisplayName("Solo fechaInicio: desde ese día en adelante")
        void soloFechaInicio() throws Exception {
            mockMvc.perform(get("/api/citas").param("fechaInicio", MARTES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Solo fechaFin: hasta ese día, incluido")
        void soloFechaFin() throws Exception {
            mockMvc.perform(get("/api/citas").param("fechaFin", LUNES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("Filtros combinados")
    class FiltrosCombinados {

        @Test
        @DisplayName("Los filtros se acumulan con AND")
        void medicoPacienteYEstado() throws Exception {
            mockMvc.perform(get("/api/citas")
                            .param("medicoId", String.valueOf(cardiologa))
                            .param("pacienteId", String.valueOf(juan))
                            .param("estado", "PROGRAMADA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Una combinación sin resultados devuelve 200 con lista vacía, no 404")
        void combinacionSinResultados() throws Exception {
            mockMvc.perform(get("/api/citas")
                            .param("medicoId", String.valueOf(pediatra))
                            .param("estado", "CANCELADA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Los cinco filtros a la vez")
        void todosLosFiltros() throws Exception {
            mockMvc.perform(get("/api/citas")
                            .param("medicoId", String.valueOf(cardiologa))
                            .param("pacienteId", String.valueOf(ana))
                            .param("estado", "CANCELADA")
                            .param("fechaInicio", LUNES)
                            .param("fechaFin", MARTES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].paciente.nombreCompleto").value("Ana Torres"));
        }
    }

    @Nested
    @DisplayName("Validación de los parámetros")
    class ValidacionDeParametros {

        @Test
        @DisplayName("Un estado inexistente devuelve 400 enumerando los válidos")
        void estadoInvalido() throws Exception {
            mockMvc.perform(get("/api/citas").param("estado", "cancelada"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                    .andExpect(jsonPath("$.mensaje").value(
                            "El parámetro 'estado' solo admite los valores: PROGRAMADA, CANCELADA, ATENDIDA."));
        }

        @Test
        @DisplayName("Un rango invertido devuelve 400")
        void rangoInvertido() throws Exception {
            mockMvc.perform(get("/api/citas")
                            .param("fechaInicio", MARTES)
                            .param("fechaFin", LUNES))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("RANGO_DE_FECHAS_INVALIDO"));
        }

        @Test
        @DisplayName("Una fecha mal formada devuelve 400, no 500")
        void fechaMalFormada() throws Exception {
            mockMvc.perform(get("/api/citas").param("fechaInicio", "03/08/2026"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        }

        @Test
        @DisplayName("Un identificador que no existe devuelve lista vacía, no error")
        void medicoInexistente() throws Exception {
            mockMvc.perform(get("/api/citas").param("medicoId", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Test
    @DisplayName("El listado resuelve en una sola consulta: el médico y el paciente vienen con la cita")
    void listadoSinConsultasPorCita() throws Exception {
        Statistics estadisticas = entityManagerFactory
                .unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();

        mockMvc.perform(get("/api/citas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                // Se leen datos de ambas asociaciones: si vinieran perezosas, cada una
                // dispararía su propia consulta.
                .andExpect(jsonPath("$[0].medico.nombreCompleto").value("Dra. María González"))
                .andExpect(jsonPath("$[0].paciente.nombreCompleto").value("Juan Pérez"));

        // Sin el @EntityGraph serían 1 + 4x2 = 9 consultas para estas cuatro citas.
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ apoyo

    private long reservar(long medico, long paciente, LocalDateTime fechaHora) throws Exception {
        String respuesta = mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"medicoId": %d, "pacienteId": %d, "fechaHora": "%s"}"""
                                .formatted(medico, paciente, fechaHora)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Integer>read(respuesta, "$.id").longValue();
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
