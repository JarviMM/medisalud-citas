package com.medisalud.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.medisalud.agenda.domain.CalendarioLaboral;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.dto.DisponibilidadResponse;
import com.medisalud.agenda.dto.DisponibilidadResponse.DiaDisponible;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ExcepcionDeDominio;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import com.medisalud.agenda.repository.CitaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Calculo de disponibilidad (RF-04).
 *
 * <p>El reloj se fija en el lunes 3 de agosto de 2026 a las 07:00, antes de que abra la
 * consulta, para que ninguna franja de ese dia quede descartada por pasada salvo en el test
 * que lo comprueba expresamente.</p>
 */
@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    private static final Clock RELOJ_ANTES_DE_ABRIR =
            Clock.fixed(Instant.parse("2026-08-03T07:00:00Z"), ZoneOffset.UTC);

    private static final LocalDate LUNES = LocalDate.of(2026, 8, 3);
    private static final LocalDate SABADO = LocalDate.of(2026, 8, 8);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 8, 9);

    private static final long MEDICO_ID = 1L;

    @Mock private MedicoService medicoService;
    @Mock private CitaRepository citaRepository;

    private DisponibilidadService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = crearServicio(RELOJ_ANTES_DE_ABRIR);
    }

    private DisponibilidadService crearServicio(Clock reloj) {
        return new DisponibilidadService(
                medicoService, citaRepository, new CalendarioLaboral(fecha -> false), reloj);
    }

    @Nested
    @DisplayName("Franjas de un día")
    class FranjasDeUnDia {

        @Test
        @DisplayName("Un día de semana sin citas ofrece las 20 franjas de 08:00 a 17:30")
        void diaDeSemanaCompleto() {
            medicoExiste();
            sinCitas();

            DisponibilidadResponse respuesta = servicio.calcular(MEDICO_ID, LUNES, LUNES);

            assertThat(respuesta.dias()).hasSize(1);
            assertThat(respuesta.totalFranjasDisponibles()).isEqualTo(20);
            assertThat(respuesta.dias().getFirst().franjas())
                    .startsWith(LocalTime.of(8, 0), LocalTime.of(8, 30))
                    .endsWith(LocalTime.of(17, 0), LocalTime.of(17, 30))
                    .hasSize(20);
        }

        @Test
        @DisplayName("El sábado ofrece 10 franjas, de 08:00 a 12:30")
        void sabadoLimitado() {
            medicoExiste();
            sinCitas();

            DisponibilidadResponse respuesta = servicio.calcular(MEDICO_ID, SABADO, SABADO);

            assertThat(respuesta.dias().getFirst().franjas())
                    .hasSize(10)
                    .endsWith(LocalTime.of(12, 30));
        }

        @Test
        @DisplayName("El domingo se omite por completo del listado de días")
        void domingoSeOmite() {
            medicoExiste();
            sinCitas();

            DisponibilidadResponse respuesta = servicio.calcular(MEDICO_ID, DOMINGO, DOMINGO);

            assertThat(respuesta.dias()).isEmpty();
            assertThat(respuesta.totalFranjasDisponibles()).isZero();
        }

        @Test
        @DisplayName("Un festivo también desaparece del listado")
        void festivoSeOmite() {
            medicoExiste();
            sinCitas();
            var conFestivo = new DisponibilidadService(
                    medicoService, citaRepository,
                    new CalendarioLaboral(fecha -> fecha.equals(LUNES)), RELOJ_ANTES_DE_ABRIR);

            assertThat(conFestivo.calcular(MEDICO_ID, LUNES, LUNES).dias()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Exclusiones")
    class Exclusiones {

        @Test
        @DisplayName("Las franjas con cita programada no se ofrecen")
        void franjasOcupadas() {
            medicoExiste();
            given(citaRepository.buscarHorasOcupadas(any(), any(), any(), any()))
                    .willReturn(List.of(LUNES.atTime(9, 0), LUNES.atTime(9, 30), LUNES.atTime(15, 0)));

            DisponibilidadResponse respuesta = servicio.calcular(MEDICO_ID, LUNES, LUNES);

            assertThat(respuesta.dias().getFirst().franjas())
                    .hasSize(17)
                    .doesNotContain(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(15, 0))
                    .contains(LocalTime.of(8, 30), LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("Las franjas que ya pasaron no se ofrecen, aunque estén libres")
        void franjasPasadas() {
            medicoExiste();
            sinCitas();
            var aMediaManana = crearServicio(
                    Clock.fixed(Instant.parse("2026-08-03T10:15:00Z"), ZoneOffset.UTC));

            List<LocalTime> franjas = aMediaManana.calcular(MEDICO_ID, LUNES, LUNES)
                    .dias().getFirst().franjas();

            assertThat(franjas)
                    .hasSize(15)
                    .startsWith(LocalTime.of(10, 30))
                    .doesNotContain(LocalTime.of(8, 0), LocalTime.of(10, 0));
        }

        @Test
        @DisplayName("Un día laborable sin franjas libres sí aparece, con la lista vacía")
        void diaCompletamenteReservado() {
            medicoExiste();
            List<LocalDateTime> todasLasFranjas = new CalendarioLaboral(fecha -> false)
                    .franjasDe(LUNES).stream()
                    .map(LUNES::atTime)
                    .toList();
            given(citaRepository.buscarHorasOcupadas(any(), any(), any(), any()))
                    .willReturn(todasLasFranjas);

            DisponibilidadResponse respuesta = servicio.calcular(MEDICO_ID, LUNES, LUNES);

            // Presente pero vacío: "todo reservado" no es lo mismo que "ese día no atendemos".
            assertThat(respuesta.dias()).hasSize(1);
            assertThat(respuesta.dias().getFirst().franjas()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Rango de fechas")
    class RangoDeFechas {

        @Test
        @DisplayName("Una semana completa devuelve seis días: el domingo no aparece")
        void semanaCompleta() {
            medicoExiste();
            sinCitas();

            DisponibilidadResponse respuesta = servicio.calcular(MEDICO_ID, LUNES, DOMINGO);

            assertThat(respuesta.dias()).extracting(DiaDisponible::fecha)
                    .containsExactly(
                            LUNES,
                            LUNES.plusDays(1),
                            LUNES.plusDays(2),
                            LUNES.plusDays(3),
                            LUNES.plusDays(4),
                            SABADO);
            // Cinco días de semana con 20 franjas más el sábado con 10.
            assertThat(respuesta.totalFranjasDisponibles()).isEqualTo(5 * 20 + 10);
        }

        @Test
        @DisplayName("Ambos extremos del rango son inclusive")
        void extremosInclusive() {
            medicoExiste();
            sinCitas();

            DisponibilidadResponse respuesta = servicio.calcular(MEDICO_ID, LUNES, LUNES.plusDays(1));

            assertThat(respuesta.dias()).hasSize(2);
            assertThat(respuesta.fechaInicio()).isEqualTo(LUNES);
            assertThat(respuesta.fechaFin()).isEqualTo(LUNES.plusDays(1));
        }

        @Test
        @DisplayName("Un rango invertido se rechaza sin llegar a la base de datos")
        void rangoInvertido() {
            assertThatThrownBy(() -> servicio.calcular(MEDICO_ID, LUNES, LUNES.minusDays(1)))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.RANGO_DE_FECHAS_INVALIDO);

            verifyNoInteractions(medicoService, citaRepository);
        }

        @Test
        @DisplayName("Un rango desmesurado se rechaza para no generar franjas sin control")
        void rangoDemasiadoAmplio() {
            assertThatThrownBy(() -> servicio.calcular(MEDICO_ID, LUNES, LUNES.plusYears(100)))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("máximo consultable");

            verifyNoInteractions(medicoService, citaRepository);
        }

        @Test
        @DisplayName("El rango máximo permitido de 90 días se acepta")
        void rangoEnElLimite() {
            medicoExiste();
            sinCitas();

            assertThat(servicio.calcular(MEDICO_ID, LUNES, LUNES.plusDays(89)).dias())
                    .isNotEmpty();
        }
    }

    // ------------------------------------------------------------------ apoyo

    private void medicoExiste() {
        given(medicoService.buscarEntidad(MEDICO_ID)).willReturn(Medico.builder()
                .id(MEDICO_ID)
                .nombreCompleto("Dra. María González")
                .especialidad("Cardiología")
                .build());
    }

    private void sinCitas() {
        given(citaRepository.buscarHorasOcupadas(any(), any(), any(), any()))
                .willReturn(List.of());
    }
}
