package com.medisalud.agenda.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone el reloj del sistema como un bean inyectable.
 *
 * <p>Practicamente todas las reglas de negocio dependen del "ahora": la antelacion de 2
 * horas para penalizar una cancelacion (RN-05), la ventana movil de 30 dias del bloqueo
 * (RN-05) y el rechazo de fechas de nacimiento futuras (RN-03). Si los servicios llamaran
 * a {@code LocalDateTime.now()} esas reglas solo podrian probarse esperando en tiempo
 * real o manipulando datos artificialmente.</p>
 *
 * <p>Inyectando un {@link Clock}, los tests sustituyen este bean por
 * {@code Clock.fixed(...)} y verifican el limite exacto de la regla (1h59m frente a
 * 2h01m, dia 29 frente a dia 31) de forma determinista.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
