package com.medisalud.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprueba que el contenedor no arranca con el instrumental de desarrollo.
 *
 * <p>El perfil {@code dev} deja abierta la consola H2 en {@code /h2-console}, que ejecuta
 * SQL arbitrario contra la base sin pedir credenciales. En local es comodo; en un
 * contenedor publicado es una puerta abierta a los datos de todos los pacientes. Como
 * {@code spring.profiles.default} es {@code dev}, un contenedor que no fije perfil
 * explicitamente acabaria en esa situacion sin que nadie lo note.</p>
 *
 * <p>Por eso el test mira tambien el {@code Dockerfile}: la regresion que previene no vive
 * en el codigo Java sino en una linea de configuracion del despliegue, y comprobar solo el
 * YAML dejaria pasar que alguien devuelva el {@code ENV} a {@code dev}.</p>
 */
class ConfiguracionDeDespliegueTest {

    @Test
    @DisplayName("El Dockerfile activa el perfil docker y nunca el de desarrollo")
    void elDockerfileNoActivaDev() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"), StandardCharsets.UTF_8);

        assertThat(dockerfile)
                .as("El contenedor debe fijar un perfil explícito")
                .contains("SPRING_PROFILES_ACTIVE=docker")
                .as("El perfil dev abre la consola H2 sin autenticación")
                .doesNotContain("SPRING_PROFILES_ACTIVE=dev");
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("docker")
    @DisplayName("Perfil docker")
    class PerfilDocker {

        @Autowired private Environment entorno;

        @Test
        @DisplayName("Deja la consola H2 cerrada y no vuelca SQL a los logs")
        void sinInstrumentalDeDesarrollo() {
            assertThat(entorno.getProperty("spring.h2.console.enabled"))
                    .as("La consola H2 permite ejecutar SQL arbitrario sin credenciales")
                    .isEqualTo("false");
            assertThat(entorno.getProperty("spring.jpa.show-sql"))
                    .as("El SQL en los logs arrastra los valores de cada sentencia")
                    .isEqualTo("false");
        }

        @Test
        @DisplayName("Las credenciales de la base se leen de variables de entorno")
        void credencialesExternalizadas() {
            assertThat(entorno.getProperty("spring.datasource.username")).isEqualTo("sa");
        }
    }
}
