package com.medisalud.agenda.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medisalud.agenda.config.ClockConfig;
import com.medisalud.agenda.dto.PacienteResponse;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.ManejadorGlobalDeErrores;
import com.medisalud.agenda.service.PacienteService;
import java.time.LocalDate;
import java.util.List;
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
 * Contrato HTTP del recurso paciente.
 */
@WebMvcTest(PacienteController.class)
@Import({ManejadorGlobalDeErrores.class, ClockConfig.class})
@ActiveProfiles("test")
class PacienteControllerTest {

    private static final String CUERPO_VALIDO = """
            {
              "nombreCompleto": "Juan Pérez",
              "documentoIdentidad": "1020304050",
              "telefono": "3001234567",
              "email": "juan.perez@example.com",
              "fechaNacimiento": "1990-05-20"
            }""";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PacienteService pacienteService;

    @Test
    @DisplayName("POST devuelve 201 con la cabecera Location")
    void creaPaciente() throws Exception {
        given(pacienteService.crear(any())).willReturn(new PacienteResponse(
                3L, "Juan Pérez", "1020304050", "3001234567", "juan.perez@example.com",
                LocalDate.of(1990, 5, 20)));

        mockMvc.perform(post("/api/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/pacientes/3")))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.fechaNacimiento").value("1990-05-20"));
    }

    @Test
    @DisplayName("Un documento ya registrado devuelve 409 sin repetir el documento en el mensaje")
    void documentoDuplicado() throws Exception {
        willThrow(new ConflictoDeNegocioException(
                CodigoError.DOCUMENTO_DUPLICADO,
                "Ya existe un paciente registrado con ese documento de identidad."))
                .given(pacienteService).crear(any());

        mockMvc.perform(post("/api/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_DUPLICADO"))
                .andExpect(jsonPath("$.ruta").value("/api/pacientes"))
                .andExpect(jsonPath("$.mensaje").value(
                        Matchers.not(Matchers.containsString("1020304050"))));
    }

    @Test
    @DisplayName("RN-03: una fecha de nacimiento futura devuelve 400")
    void fechaDeNacimientoFutura() throws Exception {
        mockMvc.perform(post("/api/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreCompleto": "Juan Pérez",
                                  "documentoIdentidad": "1020304050",
                                  "telefono": "3001234567",
                                  "email": "juan.perez@example.com",
                                  "fechaNacimiento": "2999-01-01"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles[0].campo").value("fechaNacimiento"))
                .andExpect(jsonPath("$.detalles[0].mensaje")
                        .value("La fecha de nacimiento no puede ser futura."));
    }

    @Test
    @DisplayName("Una fecha con formato incorrecto devuelve 400, no 500")
    void fechaConFormatoIncorrecto() throws Exception {
        mockMvc.perform(post("/api/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreCompleto": "Juan Pérez",
                                  "documentoIdentidad": "1020304050",
                                  "telefono": "3001234567",
                                  "email": "juan.perez@example.com",
                                  "fechaNacimiento": "20-05-1990"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
    }

    @Test
    @DisplayName("GET lista los pacientes registrados")
    void listaPacientes() throws Exception {
        given(pacienteService.listar()).willReturn(List.of(new PacienteResponse(
                3L, "Juan Pérez", "1020304050", "3001234567", "juan.perez@example.com", null)));

        mockMvc.perform(get("/api/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombreCompleto").value("Juan Pérez"))
                // fechaNacimiento nula se omite del JSON (default-property-inclusion: non_null)
                .andExpect(jsonPath("$[0].fechaNacimiento").doesNotExist());
    }
}
