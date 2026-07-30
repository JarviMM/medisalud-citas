package com.medisalud.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariantes de {@link JornadaLaboral}.
 *
 * <p>Que una jornada mal formada no se pueda construir es lo que hace innecesario
 * comprobarla despues en cada uso.</p>
 */
class JornadaLaboralTest {

    private static final Duration MEDIA_HORA = Duration.ofMinutes(30);

    @Test
    @DisplayName("Una jornada con el cierre antes de la apertura no se puede construir")
    void jornadaInvertida() {
        assertThatThrownBy(() -> new JornadaLaboral(LocalTime.of(18, 0), LocalTime.of(8, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    @DisplayName("Una jornada de duración cero tampoco es válida")
    void jornadaVacia() {
        assertThatThrownBy(() -> new JornadaLaboral(LocalTime.of(8, 0), LocalTime.of(8, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Una jornada sin horas no se puede construir")
    void jornadaSinHoras() {
        assertThatThrownBy(() -> new JornadaLaboral(null, LocalTime.of(18, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JornadaLaboral(LocalTime.of(8, 0), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Las franjas solo incluyen las que terminan dentro de la jornada")
    void ultimaFranjaCabeEntera() {
        var jornada = new JornadaLaboral(LocalTime.of(8, 0), LocalTime.of(9, 0));

        assertThat(jornada.franjas(MEDIA_HORA))
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(8, 30));
    }

    @Test
    @DisplayName("Una jornada más corta que la franja no ofrece ninguna")
    void jornadaMasCortaQueLaFranja() {
        var jornada = new JornadaLaboral(LocalTime.of(8, 0), LocalTime.of(8, 20));

        assertThat(jornada.franjas(MEDIA_HORA)).isEmpty();
    }

    @Test
    @DisplayName("La lista de franjas es inmutable")
    void franjasInmutables() {
        var franjas = new JornadaLaboral(LocalTime.of(8, 0), LocalTime.of(9, 0)).franjas(MEDIA_HORA);

        assertThatThrownBy(() -> franjas.add(LocalTime.of(9, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
