package com.medisalud.agenda.support;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Sustituye el reloj del sistema por uno que el test controla.
 *
 * <p>Se importa desde los tests de integracion que necesitan mover el tiempo. Al ser la
 * misma clase para todos, Spring reutiliza el mismo contexto entre ellos en lugar de
 * levantar uno por cada conjunto distinto de configuracion.</p>
 *
 * <p>{@code @Primary} basta para desplazar al bean de {@code ClockConfig} sin excluirlo:
 * la aplicacion sigue arrancando como en produccion y solo cambia de donde saca la hora.</p>
 */
@TestConfiguration
public class ConfiguracionDeRelojDePruebas {

    /** Lunes 3 de agosto de 2026 a las 08:00, justo cuando abre la consulta. */
    public static final LocalDateTime MOMENTO_INICIAL = LocalDateTime.of(2026, 8, 3, 8, 0);

    @Bean
    @Primary
    public RelojAjustable relojAjustable() {
        return new RelojAjustable(MOMENTO_INICIAL, ZoneOffset.UTC);
    }
}
