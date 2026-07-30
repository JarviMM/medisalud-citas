package com.medisalud.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Horario laboral y generacion de franjas (RN-01).
 *
 * <p>Sin Spring: el calendario solo necesita un {@link ProveedorDeFestivos}, que aqui se
 * pasa como lambda para poder simular un festivo sin montar contexto.</p>
 */
class CalendarioLaboralTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 8, 3);
    private static final LocalDate SABADO = LocalDate.of(2026, 8, 8);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 8, 9);

    private final CalendarioLaboral sinFestivos = new CalendarioLaboral(fecha -> false);

    @Test
    @DisplayName("Las fechas de referencia son los días de la semana que se asume")
    void fechasDeReferenciaCorrectas() {
        assertThat(LUNES.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(SABADO.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY);
        assertThat(DOMINGO.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
    }

    @Nested
    @DisplayName("Franjas del día")
    class FranjasDelDia {

        @Test
        @DisplayName("Un día de semana tiene 20 franjas, de 08:00 a 17:30")
        void diaDeSemana() {
            var franjas = sinFestivos.franjasDe(LUNES);

            assertThat(franjas).hasSize(20);
            assertThat(franjas.getFirst()).isEqualTo(LocalTime.of(8, 0));
            assertThat(franjas.getLast()).isEqualTo(LocalTime.of(17, 30));
            assertThat(franjas).doesNotContain(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("El sábado tiene 10 franjas, de 08:00 a 12:30")
        void sabado() {
            var franjas = sinFestivos.franjasDe(SABADO);

            assertThat(franjas).hasSize(10);
            assertThat(franjas.getFirst()).isEqualTo(LocalTime.of(8, 0));
            assertThat(franjas.getLast()).isEqualTo(LocalTime.of(12, 30));
            assertThat(franjas).doesNotContain(LocalTime.of(13, 0));
        }

        @Test
        @DisplayName("El domingo no tiene ninguna franja")
        void domingo() {
            assertThat(sinFestivos.franjasDe(DOMINGO)).isEmpty();
            assertThat(sinFestivos.esDiaHabil(DOMINGO)).isFalse();
        }

        @Test
        @DisplayName("Las franjas van de 30 en 30 minutos sin huecos")
        void franjasConsecutivas() {
            var franjas = sinFestivos.franjasDe(LUNES);

            for (int i = 1; i < franjas.size(); i++) {
                assertThat(franjas.get(i)).isEqualTo(franjas.get(i - 1).plusMinutes(30));
            }
        }
    }

    @Nested
    @DisplayName("Festivos")
    class Festivos {

        @Test
        @DisplayName("Un festivo deja el día sin atención aunque sea laborable")
        void festivoAnulaLaJornada() {
            var conFestivo = new CalendarioLaboral(fecha -> fecha.equals(LUNES));

            assertThat(conFestivo.esDiaHabil(LUNES)).isFalse();
            assertThat(conFestivo.franjasDe(LUNES)).isEmpty();
            assertThat(conFestivo.jornadaDe(LUNES)).isEmpty();
        }

        @Test
        @DisplayName("El proveedor por defecto no marca ningún día como festivo")
        void sinFestivosTodoDiaLaborableAtiende() {
            assertThat(sinFestivos.esDiaHabil(LUNES)).isTrue();
        }
    }

    @Nested
    @DisplayName("Alineación de la franja")
    class AlineacionDeFranja {

        @ParameterizedTest(name = "{0} es inicio de franja")
        @ValueSource(strings = {"2026-08-03T08:00:00", "2026-08-03T08:30:00", "2026-08-03T17:30:00"})
        void horasAlineadas(String fechaHora) {
            assertThat(sinFestivos.esInicioDeFranja(LocalDateTime.parse(fechaHora))).isTrue();
        }

        @ParameterizedTest(name = "{0} NO es inicio de franja")
        @ValueSource(strings = {"2026-08-03T08:15:00", "2026-08-03T08:01:00", "2026-08-03T08:00:30"})
        void horasDesalineadas(String fechaHora) {
            assertThat(sinFestivos.esInicioDeFranja(LocalDateTime.parse(fechaHora))).isFalse();
        }
    }

    @Nested
    @DisplayName("Pertenencia a la jornada")
    class PertenenciaALaJornada {

        @Test
        @DisplayName("La última franja del día laborable termina justo al cierre")
        void ultimaFranjaDelDia() {
            assertThat(sinFestivos.estaDentroDeLaJornada(LUNES.atTime(17, 30))).isTrue();
            assertThat(sinFestivos.estaDentroDeLaJornada(LUNES.atTime(18, 0))).isFalse();
        }

        @Test
        @DisplayName("El sábado por la tarde ya no se atiende")
        void sabadoPorLaTarde() {
            assertThat(sinFestivos.estaDentroDeLaJornada(SABADO.atTime(12, 30))).isTrue();
            assertThat(sinFestivos.estaDentroDeLaJornada(SABADO.atTime(13, 0))).isFalse();
            assertThat(sinFestivos.estaDentroDeLaJornada(SABADO.atTime(16, 0))).isFalse();
        }

        @Test
        @DisplayName("Antes de abrir no se atiende")
        void antesDeAbrir() {
            assertThat(sinFestivos.estaDentroDeLaJornada(LUNES.atTime(7, 30))).isFalse();
            assertThat(sinFestivos.estaDentroDeLaJornada(LUNES.atTime(8, 0))).isTrue();
        }

        @Test
        @DisplayName("El domingo no se atiende a ninguna hora")
        void domingoNuncaSeAtiende() {
            assertThat(sinFestivos.estaDentroDeLaJornada(DOMINGO.atTime(10, 0))).isFalse();
        }
    }
}
