package com.medisalud.agenda.config;

import com.medisalud.agenda.domain.ProveedorDeFestivos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registro del calendario de festivos.
 */
@Configuration
public class CalendarioConfig {

    /**
     * Implementacion por defecto: no hay festivos.
     *
     * <p>El enunciado no aporta lista de festivos y pide dejar el punto de extension
     * preparado. {@code @ConditionalOnMissingBean} consigue justo eso: declarar en
     * cualquier parte otro bean {@link ProveedorDeFestivos} (uno que consulte una tabla o
     * un servicio externo) desactiva este stub sin tocar ni una linea del dominio.</p>
     */
    @Bean
    @ConditionalOnMissingBean(ProveedorDeFestivos.class)
    public ProveedorDeFestivos proveedorDeFestivosVacio() {
        return fecha -> false;
    }
}
