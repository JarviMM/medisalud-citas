package com.medisalud.agenda.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medisalud.agenda.config.ClockConfig;
import com.medisalud.agenda.dto.MedicoResponse;
import com.medisalud.agenda.exception.ManejadorGlobalDeErrores;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.service.MedicoService;
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
 * Contrato HTTP del recurso medico y del manejador global de errores.
 *
 * <p>Es un test de rodaja web: no levanta la base de datos ni el resto del contexto, solo
 * la capa MVC. El servicio se sustituye por un doble para poder provocar a voluntad los
 * escenarios de error sin montar datos.</p>
 *
 * <p>{@link ClockConfig} se importa explicitamente porque {@code @WebMvcTest} solo carga
 * componentes web, y el manejador de errores necesita el {@code Clock} para sellar la
 * respuesta.</p>
 */
@WebMvcTest(MedicoController.class)
@Import({ManejadorGlobalDeErrores.class, ClockConfig.class})
@ActiveProfiles("test")
class MedicoControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MedicoService medicoService;

    @Test
    @DisplayName("POST devuelve 201, la cabecera Location y el recurso creado")
    void creaMedico() throws Exception {
        given(medicoService.crear(any())).willReturn(new MedicoResponse(
                7L, "Dra. María González", "Cardiología", "555-1001", "maria.gonzalez@medisalud.com"));

        mockMvc.perform(post("/api/medicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreCompleto": "Dra. María González",
                                  "especialidad": "Cardiología",
                                  "telefono": "555-1001",
                                  "email": "maria.gonzalez@medisalud.com"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/medicos/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nombreCompleto").value("Dra. María González"));
    }

    @Test
    @DisplayName("POST inválido devuelve 400 con el detalle por campo y no llega al servicio")
    void rechazaMedicoInvalido() throws Exception {
        mockMvc.perform(post("/api/medicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreCompleto": "Ab",
                                  "especialidad": "",
                                  "telefono": "123",
                                  "email": "no-es-un-email"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.ruta").value("/api/medicos"))
                .andExpect(jsonPath("$.detalles.length()").value(4))
                .andExpect(jsonPath("$.detalles[*].campo").value(Matchers.containsInAnyOrder(
                        "nombreCompleto", "especialidad", "telefono", "email")));

        verifyNoInteractions(medicoService);
    }

    @Test
    @DisplayName("GET de un médico inexistente devuelve 404 con el formato de error común")
    void medicoInexistente() throws Exception {
        willThrow(new RecursoNoEncontradoException("el médico", 99L))
                .given(medicoService).obtenerPorId(99L);

        mockMvc.perform(get("/api/medicos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No se encontró el médico con id 99."))
                .andExpect(jsonPath("$.ruta").value("/api/medicos/99"))
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    @DisplayName("Un id con formato inválido devuelve 400, no 500")
    void idConFormatoInvalido() throws Exception {
        mockMvc.perform(get("/api/medicos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("El parámetro 'id' no tiene un formato válido."));

        verifyNoInteractions(medicoService);
    }

    @Test
    @DisplayName("Un JSON malformado devuelve 400 sin filtrar detalles internos")
    void cuerpoMalformado() throws Exception {
        mockMvc.perform(post("/api/medicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(Matchers.not(Matchers.containsString("com."))));
    }

    @Test
    @DisplayName("Un método no soportado también respeta el formato de error común")
    void metodoNoSoportado() throws Exception {
        mockMvc.perform(delete("/api/medicos/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
