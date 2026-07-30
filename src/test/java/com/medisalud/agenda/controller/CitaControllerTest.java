package com.medisalud.agenda.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medisalud.agenda.config.ClockConfig;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.dto.CitaResponse;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.ManejadorGlobalDeErrores;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import com.medisalud.agenda.service.CitaService;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP del recurso cita: sobre todo, que cada regla de negocio salga con el codigo
 * de estado que le corresponde.
 */
@WebMvcTest(CitaController.class)
@Import({ManejadorGlobalDeErrores.class, ClockConfig.class})
@ActiveProfiles("test")
class CitaControllerTest {

    private static final String CUERPO_VALIDO = """
            {"medicoId": 1, "pacienteId": 2, "fechaHora": "2026-08-03T09:00:00"}""";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CitaService citaService;

    @Test
    @DisplayName("POST devuelve 201 con Location y la cita creada")
    void reservaCita() throws Exception {
        given(citaService.reservar(any())).willReturn(unaCitaProgramada());

        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/citas/10")))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.fechaHora").value("2026-08-03T09:00:00"))
                .andExpect(jsonPath("$.medico.nombreCompleto").value("Dra. María González"))
                .andExpect(jsonPath("$.paciente.nombreCompleto").value("Juan Pérez"))
                // los campos nulos no viajan en el JSON
                .andExpect(jsonPath("$.fechaCancelacion").doesNotExist())
                .andExpect(jsonPath("$.citaOrigenId").doesNotExist());
    }

    @Test
    @DisplayName("Faltar identificadores o fecha devuelve 400 con detalle por campo")
    void cuerpoIncompleto() throws Exception {
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles[*].campo").value(Matchers.containsInAnyOrder(
                        "medicoId", "pacienteId", "fechaHora")));

        verifyNoInteractions(citaService);
    }

    @Test
    @DisplayName("RN-01: una franja desalineada devuelve 400, no 409")
    void franjaDesalineada() throws Exception {
        willThrow(new SolicitudInvalidaException(CodigoError.FRANJA_NO_VALIDA,
                "La hora debe coincidir con el inicio de una franja de 30 minutos (por ejemplo 09:00 o 09:30)."))
                .given(citaService).reservar(any());

        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"medicoId": 1, "pacienteId": 2, "fechaHora": "2026-08-03T08:15:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FRANJA_NO_VALIDA"));
    }

    @Test
    @DisplayName("RN-01: un domingo devuelve 400 porque nunca podrá tener éxito")
    void domingoSinAtencion() throws Exception {
        willThrow(new SolicitudInvalidaException(CodigoError.FUERA_DE_HORARIO_LABORAL,
                "El 2026-08-09 no hay atención."))
                .given(citaService).reservar(any());

        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"medicoId": 1, "pacienteId": 2, "fechaHora": "2026-08-09T10:00:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_HORARIO_LABORAL"));
    }

    @Test
    @DisplayName("RN-02: franja ocupada por el médico devuelve 409")
    void medicoNoDisponible() throws Exception {
        willThrow(new ConflictoDeNegocioException(CodigoError.MEDICO_NO_DISPONIBLE,
                "El médico ya tiene una cita programada en esa franja."))
                .given(citaService).reservar(any());

        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MEDICO_NO_DISPONIBLE"));
    }

    @Test
    @DisplayName("RN-04: el paciente ya tiene cita en esa franja devuelve 409")
    void pacienteNoDisponible() throws Exception {
        willThrow(new ConflictoDeNegocioException(CodigoError.PACIENTE_NO_DISPONIBLE,
                "El paciente ya tiene una cita programada en esa franja con otro médico."))
                .given(citaService).reservar(any());

        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PACIENTE_NO_DISPONIBLE"));
    }

    @Test
    @DisplayName("Un médico inexistente devuelve 404")
    void medicoInexistente() throws Exception {
        willThrow(new RecursoNoEncontradoException("el médico", 99L))
                .given(citaService).reservar(any());

        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"medicoId": 99, "pacienteId": 2, "fechaHora": "2026-08-03T09:00:00"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("GET devuelve la cita")
    void obtieneCita() throws Exception {
        given(citaService.obtenerPorId(10L)).willReturn(unaCitaProgramada());

        mockMvc.perform(get("/api/citas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.paciente.documentoIdentidad").value("1020304050"));
    }

    private static CitaResponse unaCitaProgramada() {
        return new CitaResponse(
                10L,
                new CitaResponse.MedicoResumen(1L, "Dra. María González", "Cardiología"),
                new CitaResponse.PacienteResumen(2L, "Juan Pérez", "1020304050"),
                LocalDateTime.of(2026, 8, 3, 9, 0),
                EstadoCita.PROGRAMADA,
                null,
                null);
    }
}
