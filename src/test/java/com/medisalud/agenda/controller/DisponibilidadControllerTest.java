package com.medisalud.agenda.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medisalud.agenda.config.ClockConfig;
import com.medisalud.agenda.dto.DisponibilidadResponse;
import com.medisalud.agenda.dto.DisponibilidadResponse.DiaDisponible;
import com.medisalud.agenda.dto.MedicoResumen;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ManejadorGlobalDeErrores;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import com.medisalud.agenda.service.DisponibilidadService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP de la consulta de disponibilidad.
 */
@WebMvcTest(DisponibilidadController.class)
@Import({ManejadorGlobalDeErrores.class, ClockConfig.class})
@ActiveProfiles("test")
class DisponibilidadControllerTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 8, 3);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DisponibilidadService disponibilidadService;

    @Test
    @DisplayName("Devuelve las franjas agrupadas por día")
    void devuelveDisponibilidad() throws Exception {
        given(disponibilidadService.calcular(eq(1L), eq(LUNES), eq(LUNES)))
                .willReturn(new DisponibilidadResponse(
                        new MedicoResumen(1L, "Dra. María González", "Cardiología"),
                        LUNES,
                        LUNES,
                        3,
                        List.of(new DiaDisponible(LUNES, List.of(
                                LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0))))));

        mockMvc.perform(get("/api/medicos/1/disponibilidad")
                        .param("fechaInicio", "2026-08-03")
                        .param("fechaFin", "2026-08-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medico.nombreCompleto").value("Dra. María González"))
                .andExpect(jsonPath("$.totalFranjasDisponibles").value(3))
                .andExpect(jsonPath("$.dias.length()").value(1))
                .andExpect(jsonPath("$.dias[0].fecha").value("2026-08-03"))
                .andExpect(jsonPath("$.dias[0].franjas.length()").value(3))
                // Las franjas viajan como hora ISO completa, de modo que fecha + "T" + franja
                // produce exactamente el formato que espera fechaHora al reservar.
                .andExpect(jsonPath("$.dias[0].franjas[0]").value("08:00:00"))
                .andExpect(jsonPath("$.dias[0].franjas[1]").value("08:30:00"));
    }

    @Test
    @DisplayName("Falta un parámetro obligatorio: 400 con el nombre del parámetro")
    void faltaParametro() throws Exception {
        mockMvc.perform(get("/api/medicos/1/disponibilidad")
                        .param("fechaInicio", "2026-08-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("Falta el parámetro obligatorio 'fechaFin'."));

        verifyNoInteractions(disponibilidadService);
    }

    @Test
    @DisplayName("Una fecha con formato incorrecto devuelve 400, no 500")
    void fechaMalFormada() throws Exception {
        mockMvc.perform(get("/api/medicos/1/disponibilidad")
                        .param("fechaInicio", "03-08-2026")
                        .param("fechaFin", "2026-08-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("El parámetro 'fechaInicio' no tiene un formato válido."));

        verifyNoInteractions(disponibilidadService);
    }

    @Test
    @DisplayName("Un rango inválido devuelve 400 con su propio código")
    void rangoInvalido() throws Exception {
        willThrow(new SolicitudInvalidaException(CodigoError.RANGO_DE_FECHAS_INVALIDO,
                "La fecha de fin no puede ser anterior a la de inicio."))
                .given(disponibilidadService).calcular(any(), any(), any());

        mockMvc.perform(get("/api/medicos/1/disponibilidad")
                        .param("fechaInicio", "2026-08-10")
                        .param("fechaFin", "2026-08-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_DE_FECHAS_INVALIDO"));
    }

    @Test
    @DisplayName("Un médico inexistente devuelve 404")
    void medicoInexistente() throws Exception {
        willThrow(new RecursoNoEncontradoException("el médico", 99L))
                .given(disponibilidadService).calcular(any(), any(), any());

        mockMvc.perform(get("/api/medicos/99/disponibilidad")
                        .param("fechaInicio", "2026-08-03")
                        .param("fechaFin", "2026-08-03"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
