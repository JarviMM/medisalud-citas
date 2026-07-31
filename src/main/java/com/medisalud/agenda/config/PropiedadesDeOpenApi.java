package com.medisalud.agenda.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Entornos que Swagger UI ofrece en su selector de servidor.
 *
 * <p>Son <b>adicionales</b> al que springdoc deduce de la peticion en curso, que sigue
 * apareciendo el primero y por tanto es el que queda seleccionado por defecto. Esa
 * distincion importa: si la lista sustituyera al deducido, abrir Swagger desde el dominio
 * publico dispararia las pruebas contra otro entorno, que es justo el problema que tenia
 * antes fijar {@code servers} a mano.</p>
 *
 * @param servidores entornos alternativos; vacio si no se declara ninguno
 */
@ConfigurationProperties(prefix = "medisalud.openapi")
public record PropiedadesDeOpenApi(List<Servidor> servidores) {

    public PropiedadesDeOpenApi {
        servidores = servidores == null ? List.of() : List.copyOf(servidores);
    }

    /**
     * @param url        direccion base del entorno
     * @param descripcion texto que se ve en el desplegable de Swagger UI
     */
    public record Servidor(String url, String descripcion) {
    }
}
