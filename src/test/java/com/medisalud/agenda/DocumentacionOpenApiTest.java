package com.medisalud.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que el documento OpenAPI describe la API que realmente existe.
 *
 * <p>Se escribio a raiz de un fallo concreto: springdoc asigna por defecto a cada respuesta
 * declarada el tipo que devuelve el metodo, asi que los 409 aparecian documentados como si
 * devolvieran una cita. Eso no es una omision, es documentacion incorrecta, y es peor que no
 * tener ninguna porque un cliente la creeria. El personalizador que lo corrige es facil de
 * romper sin darse cuenta, de modo que aqui queda fijado.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentacionOpenApiTest {

    private static final String REFERENCIA_ERROR = "#/components/schemas/ErrorResponse";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("El documento se genera y describe los nueve endpoints del enunciado")
    void documentoCompleto() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MediSalud · API de agendamiento de citas"))
                .andExpect(jsonPath("$.paths['/api/medicos']").exists())
                .andExpect(jsonPath("$.paths['/api/medicos/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/medicos/{medicoId}/disponibilidad']").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes']").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/citas']").exists())
                .andExpect(jsonPath("$.paths['/api/citas/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/citas/{id}/cancelar']").exists())
                .andExpect(jsonPath("$.paths['/api/citas/{id}/reprogramar']").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
    }

    @Test
    @DisplayName("Toda respuesta 4xx o 5xx documentada apunta al esquema de error, no al de éxito")
    void respuestasDeErrorConSuEsquema() throws Exception {
        JsonNode documento = leerDocumento();
        List<String> revisadas = new ArrayList<>();

        for (Map.Entry<String, JsonNode> ruta : documento.get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operacion : ruta.getValue().properties()) {
                JsonNode respuestas = operacion.getValue().get("responses");
                if (respuestas == null) {
                    continue;
                }
                for (Map.Entry<String, JsonNode> respuesta : respuestas.properties()) {
                    String codigo = respuesta.getKey();
                    if (!codigo.startsWith("4") && !codigo.startsWith("5")) {
                        continue;
                    }
                    String etiqueta = "%s %s -> %s".formatted(
                            operacion.getKey().toUpperCase(), ruta.getKey(), codigo);
                    revisadas.add(etiqueta);

                    // "application/json" lleva la barra escapada como ~1 en un JSON Pointer.
                    String referencia = respuesta.getValue()
                            .at("/content/application~1json/schema/$ref").asText();

                    assertThat(referencia)
                            .as("Esquema documentado para %s", etiqueta)
                            .isEqualTo(REFERENCIA_ERROR);
                }
            }
        }

        // Si el recorrido no encontrara ninguna, el test pasaría sin comprobar nada.
        assertThat(revisadas).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Los DTOs de entrada llevan ejemplos, para que Swagger UI sea usable sin leer el README")
    void ejemplosEnLasPeticiones() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CrearMedicoRequest.properties.nombreCompleto.example")
                        .value("Dra. María González"))
                .andExpect(jsonPath("$.components.schemas.CrearPacienteRequest.properties.documentoIdentidad.example")
                        .value("1020304050"))
                .andExpect(jsonPath("$.components.schemas.CrearCitaRequest.properties.fechaHora.example")
                        .value("2026-08-03T09:00:00"))
                .andExpect(jsonPath("$.components.schemas.ReprogramarCitaRequest.properties.nuevaFechaHora.example")
                        .value("2026-08-03T11:00:00"));
    }

    @Test
    @DisplayName("Swagger UI responde")
    void interfazDisponible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private JsonNode leerDocumento() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }
}
