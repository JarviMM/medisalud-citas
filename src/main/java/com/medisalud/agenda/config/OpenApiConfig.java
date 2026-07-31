package com.medisalud.agenda.config;

import com.medisalud.agenda.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion de la API.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_ERROR = "ErrorResponse";
    private static final String APPLICATION_JSON = "application/json";

    @Bean
    public OpenAPI documentacionDeLaApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MediSalud · API de agendamiento de citas")
                        .version("1.0.0")
                        .description("""
                                Sistema de reserva, cancelación y reprogramación de citas médicas.

                                **Reglas de negocio implementadas**

                                - **RN-01** Horario de atención: lunes a viernes de 08:00 a 18:00 y sábados de
                                  08:00 a 13:00. Domingos y festivos sin atención. Las citas ocupan franjas de
                                  30 minutos y deben empezar en punto o y media.
                                - **RN-02** Un médico no puede tener dos citas programadas en la misma franja.
                                - **RN-03** La fecha de nacimiento es opcional; su ausencia equivale a edad 0,
                                  que es válida. No se admite una fecha de nacimiento futura.
                                - **RN-04** Un paciente no puede tener dos citas programadas en la misma franja,
                                  aunque sean con médicos distintos.
                                - **RN-05** Cancelar con menos de 2 horas de antelación registra una penalización.
                                  Con 3 penalizaciones vigentes en los últimos 30 días el paciente no puede
                                  agendar citas nuevas hasta que la más antigua de ellas expire.
                                - **RN-06** Reprogramar cancela la cita original y crea una nueva enlazada a ella.
                                  Si el nuevo horario no está libre, la operación completa se deshace.

                                **Errores**

                                Todas las respuestas de error comparten el mismo cuerpo (`ErrorResponse`), con un
                                campo `codigo` estable pensado para programar contra él, además del mensaje
                                legible. Se usa **400** cuando la petición fallaría siempre haga lo que haga el
                                sistema (un domingo, una hora desalineada) y **409** cuando choca con el estado
                                actual y podría funcionar más tarde (franja ocupada, paciente bloqueado).
                                """)
                        .license(new License().name("MIT")));
        // Deliberadamente sin `servers`: fijar la lista a mano congelaba el
        // documento en http://localhost:8080, de modo que Swagger UI servido
        // desde el dominio público apuntaba las pruebas a la máquina de quien lo
        // abría. Sin esa lista, springdoc deduce el servidor de la petición en
        // curso y funciona igual en local que detrás del proxy.
    }

    /**
     * Asigna el esquema {@link ErrorResponse} a todas las respuestas de error de todos los
     * endpoints.
     *
     * <p>La alternativa era repetir en cada operacion un {@code @ApiResponse} con su
     * {@code @Content} y su {@code @Schema}: unas cuarenta lineas de anotacion que dicen
     * siempre lo mismo y que es facil olvidar al anadir un endpoint. Resolverlo de una vez
     * garantiza que la documentacion no pueda desviarse del contrato real, que es
     * precisamente que <b>ningun</b> error se sale del formato comun.</p>
     *
     * <p>El contenido se <b>sustituye</b>, no se rellena solo cuando falta: springdoc asigna
     * por defecto a cada respuesta declarada el tipo que devuelve el metodo, de modo que sin
     * esto un 409 aparecia documentado como si devolviera una cita. Eso no es una omision,
     * es documentacion incorrecta, y es peor que no tener ninguna.</p>
     */
    @Bean
    public OpenApiCustomizer esquemaComunDeErrores() {
        return openApi -> {
            registrarEsquemaDeError(openApi);

            Content contenido = new Content().addMediaType(APPLICATION_JSON,
                    new MediaType().schema(new Schema<>().$ref(referenciaAlEsquemaDeError())));

            openApi.getPaths().values().stream()
                    .flatMap(ruta -> ruta.readOperations().stream())
                    .filter(operacion -> operacion.getResponses() != null)
                    .forEach(operacion -> operacion.getResponses().forEach((codigo, respuesta) -> {
                        if (esRespuestaDeError(codigo)) {
                            respuesta.setContent(contenido);
                        }
                    }));
        };
    }

    /**
     * Registra el esquema en el documento. Sin esto no aparece en
     * {@code components.schemas}, porque ninguna operacion lo declara como tipo de retorno y
     * springdoc solo genera los esquemas que encuentra referenciados.
     */
    private void registrarEsquemaDeError(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        ModelConverters.getInstance()
                .readAll(ErrorResponse.class)
                .forEach(openApi.getComponents()::addSchemas);
    }

    private String referenciaAlEsquemaDeError() {
        return "#/components/schemas/" + ESQUEMA_ERROR;
    }

    private boolean esRespuestaDeError(String codigo) {
        return codigo != null && (codigo.startsWith("4") || codigo.startsWith("5"));
    }
}
