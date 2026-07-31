package com.medisalud.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Nested
    @DisplayName("Selector de servidor")
    class SelectorDeServidor {

        @Test
        @DisplayName("El servidor de la petición va primero y los declarados detrás")
        void elDeducidoEncabezaLaLista() throws Exception {
            JsonNode servidores = leerDocumento().get("servers");

            // El primero es el que Swagger UI deja seleccionado: tiene que ser
            // aquel desde el que se abrió la documentación, no un entorno ajeno.
            assertThat(servidores.get(0).get("description").asText())
                    .isEqualTo("Generated server url");

            assertThat(urlsDe(servidores))
                    .contains("https://medisalud.cuantio.net", "http://localhost:8080");
        }

        @Test
        @DisplayName("No hay servidores repetidos")
        void sinDuplicados() throws Exception {
            List<String> urls = urlsDe(leerDocumento().get("servers"));

            assertThat(urls).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Pedir el documento dos veces no acumula entradas")
        void noSeAcumulanEntrePeticiones() throws Exception {
            int primera = leerDocumento().get("servers").size();
            int segunda = leerDocumento().get("servers").size();

            // El documento se regenera en cada petición (springdoc.cache.disabled),
            // así que el personalizador vuelve a correr sobre una lista limpia.
            assertThat(segunda).isEqualTo(primera);
        }

        private List<String> urlsDe(JsonNode servidores) {
            List<String> urls = new ArrayList<>();
            servidores.forEach(servidor -> urls.add(servidor.get("url").asText()));
            return urls;
        }
    }

    /**
     * Comportamiento detras de un proxy que termina TLS, como el tunel de Cloudflare del
     * despliegue. La estrategia de cabeceras reenviadas no se activa por defecto: se
     * declara en el entorno de despliegue, asi que aqui se fuerza para poder probarla.
     */
    @Nested
    @SpringBootTest(properties = "server.forward-headers-strategy=framework")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("Detrás de un proxy que termina TLS")
    class DetrasDeUnProxy {

        @Autowired private MockMvc mockMvc;

        @Test
        @DisplayName("El documento anuncia el dominio público, no el host interno")
        void anunciaElDominioPublico() throws Exception {
            // Sin esto, Swagger UI servido por https publicaría un servidor http y el
            // navegador bloquearía cada "Try it out" por contenido mixto.
            mockMvc.perform(get("/v3/api-docs")
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "medisalud.cuantio.net")
                            .header("X-Forwarded-Port", "443"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.servers[0].url").value("https://medisalud.cuantio.net"));
        }

        @Test
        @DisplayName("La cabecera Location de un 201 sale con el esquema y host públicos")
        void locationConEsquemaPublico() throws Exception {
            mockMvc.perform(post("/api/medicos")
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "medisalud.cuantio.net")
                            .header("X-Forwarded-Port", "443")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nombreCompleto":"Dra. Proxy Test","especialidad":"Cardiología"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            Matchers.startsWith("https://medisalud.cuantio.net/api/medicos/")));
        }
    }

    private JsonNode leerDocumento() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }
}
