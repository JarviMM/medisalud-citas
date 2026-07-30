package com.medisalud.agenda.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.medisalud.agenda.domain.CalendarioLaboral;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.ExcepcionDeDominio;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.service.PoliticaDePenalizaciones;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reglas de agendamiento RN-01 a RN-05.
 *
 * <p>El reloj se fija en el lunes 3 de agosto de 2026 a las 07:00. Sin un {@link Clock}
 * inyectado, la regla de "no agendar en el pasado" solo podria probarse con fechas
 * relativas a la hora real de ejecucion, y los tests caducarian con el tiempo.</p>
 */
@ExtendWith(MockitoExtension.class)
class ValidadorDeAgendamientoTest {

    /** Lunes 3 de agosto de 2026, 07:00: antes de que abra la consulta. */
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-08-03T07:00:00Z"), ZoneOffset.UTC);

    private static final LocalDate LUNES = LocalDate.of(2026, 8, 3);
    private static final LocalDate VIERNES_ANTERIOR = LocalDate.of(2026, 7, 31);
    private static final LocalDate SABADO = LocalDate.of(2026, 8, 8);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 8, 9);

    private static final LocalDateTime FRANJA_VALIDA = LUNES.atTime(9, 0);

    @Mock private CitaRepository citaRepository;
    @Mock private PoliticaDePenalizaciones politicaDePenalizaciones;

    private ValidadorDeAgendamiento validador;

    @BeforeEach
    void prepararValidador() {
        validador = crearValidador(new CalendarioLaboral(fecha -> false));
    }

    private ValidadorDeAgendamiento crearValidador(CalendarioLaboral calendario) {
        return new ValidadorDeAgendamiento(
                calendario, citaRepository, politicaDePenalizaciones, RELOJ);
    }

    @Test
    @DisplayName("Una solicitud que cumple todas las reglas no lanza nada")
    void solicitudValida() {
        sinBloqueo();
        agendasLibres();

        assertThatCode(() -> validador.validar(unMedico(), unPaciente(null), FRANJA_VALIDA))
                .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("RN-01: horario laboral y franjas de 30 minutos")
    class HorarioLaboral {

        @Test
        @DisplayName("Una hora desalineada se rechaza con 400 y sin consultar la base de datos")
        void horaDesalineada() {
            assertThat(codigoDe(() -> validador.validar(
                    unMedico(), unPaciente(null), LUNES.atTime(8, 15))))
                    .isEqualTo(CodigoError.FRANJA_NO_VALIDA);

            verifyNoInteractions(citaRepository, politicaDePenalizaciones);
        }

        @Test
        @DisplayName("El domingo no hay atención")
        void domingo() {
            assertThat(codigoDe(() -> validador.validar(
                    unMedico(), unPaciente(null), DOMINGO.atTime(10, 0))))
                    .isEqualTo(CodigoError.FUERA_DE_HORARIO_LABORAL);
        }

        @Test
        @DisplayName("Después del cierre de un día de semana no hay atención")
        void despuesDelCierre() {
            assertThat(codigoDe(() -> validador.validar(
                    unMedico(), unPaciente(null), LUNES.atTime(19, 0))))
                    .isEqualTo(CodigoError.FUERA_DE_HORARIO_LABORAL);
        }

        @Test
        @DisplayName("El sábado por la tarde no hay atención, aunque sea día hábil")
        void sabadoPorLaTarde() {
            assertThat(codigoDe(() -> validador.validar(
                    unMedico(), unPaciente(null), SABADO.atTime(15, 0))))
                    .isEqualTo(CodigoError.FUERA_DE_HORARIO_LABORAL);
        }

        @Test
        @DisplayName("El sábado por la mañana sí hay atención")
        void sabadoPorLaManana() {
            sinBloqueo();
            agendasLibres();

            assertThatCode(() -> validador.validar(
                    unMedico(), unPaciente(null), SABADO.atTime(9, 0)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Un festivo deja el día sin atención")
        void festivo() {
            var conFestivo = crearValidador(new CalendarioLaboral(fecha -> fecha.equals(LUNES)));

            assertThat(codigoDe(() -> conFestivo.validar(
                    unMedico(), unPaciente(null), FRANJA_VALIDA)))
                    .isEqualTo(CodigoError.FUERA_DE_HORARIO_LABORAL);
        }
    }

    @Nested
    @DisplayName("Supuesto: no se agenda en el pasado")
    class CitasEnElPasado {

        @Test
        @DisplayName("Una franja ya pasada se rechaza antes que cualquier otra regla")
        void franjaPasada() {
            assertThat(codigoDe(() -> validador.validar(
                    unMedico(), unPaciente(null), VIERNES_ANTERIOR.atTime(9, 0))))
                    .isEqualTo(CodigoError.FECHA_EN_EL_PASADO);

            verifyNoInteractions(citaRepository, politicaDePenalizaciones);
        }
    }

    @Nested
    @DisplayName("RN-03: edad del paciente")
    class EdadDelPaciente {

        @Test
        @DisplayName("Sin fecha de nacimiento se agenda igual: edad 0 es válida")
        void sinFechaDeNacimiento() {
            sinBloqueo();
            agendasLibres();

            assertThatCode(() -> validador.validar(unMedico(), unPaciente(null), FRANJA_VALIDA))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Una fecha de nacimiento futura bloquea el agendamiento")
        void fechaDeNacimientoFutura() {
            Paciente paciente = unPaciente(LocalDate.of(2027, 1, 1));

            assertThat(codigoDe(() -> validador.validar(unMedico(), paciente, FRANJA_VALIDA)))
                    .isEqualTo(CodigoError.FECHA_NACIMIENTO_INVALIDA);
        }

        @Test
        @DisplayName("Un recién nacido puede agendar")
        void recienNacido() {
            sinBloqueo();
            agendasLibres();

            assertThatCode(() -> validador.validar(unMedico(), unPaciente(LUNES), FRANJA_VALIDA))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("RN-05: paciente bloqueado por cancelaciones tardías")
    class PacienteBloqueado {

        @Test
        @DisplayName("Un paciente bloqueado no puede agendar y recibe el motivo exacto")
        void bloqueadoNoAgenda() {
            EstadoDeBloqueo bloqueo = EstadoDeBloqueo.conBloqueo(
                    3, LocalDateTime.of(2026, 8, 15, 14, 30));
            given(politicaDePenalizaciones.evaluarBloqueo(2L)).willReturn(bloqueo);
            given(politicaDePenalizaciones.describirBloqueo(bloqueo))
                    .willReturn("No puede agendar hasta el 15/08/2026 a las 14:30.");

            assertThatThrownBy(() -> validador.validar(unMedico(), unPaciente(null), FRANJA_VALIDA))
                    .isInstanceOf(ConflictoDeNegocioException.class)
                    .hasMessage("No puede agendar hasta el 15/08/2026 a las 14:30.")
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.PACIENTE_BLOQUEADO);
        }

        @Test
        @DisplayName("El bloqueo se comprueba antes que la ocupación de las agendas")
        void elBloqueoTienePrioridadSobreElSolapamiento() {
            EstadoDeBloqueo bloqueo = EstadoDeBloqueo.conBloqueo(
                    3, LocalDateTime.of(2026, 8, 15, 14, 30));
            given(politicaDePenalizaciones.evaluarBloqueo(2L)).willReturn(bloqueo);
            given(politicaDePenalizaciones.describirBloqueo(bloqueo)).willReturn("bloqueado");

            assertThat(codigoDe(() -> validador.validar(unMedico(), unPaciente(null), FRANJA_VALIDA)))
                    .isEqualTo(CodigoError.PACIENTE_BLOQUEADO);

            // Al paciente bloqueado no le sirve saber que además la franja estaba ocupada.
            verifyNoInteractions(citaRepository);
        }

        @Test
        @DisplayName("Con menos de tres penalizaciones vigentes se agenda con normalidad")
        void noBloqueadoAgenda() {
            given(politicaDePenalizaciones.evaluarBloqueo(2L))
                    .willReturn(EstadoDeBloqueo.sinBloqueo(2));
            agendasLibres();

            assertThatCode(() -> validador.validar(unMedico(), unPaciente(null), FRANJA_VALIDA))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("RN-02 y RN-04: solapamiento de agendas")
    class Solapamientos {

        @Test
        @DisplayName("RN-02: el médico ya tiene una cita programada en esa franja")
        void medicoOcupado() {
            sinBloqueo();
            given(citaRepository.existsByMedicoIdAndFechaHoraAndEstado(
                    eq(1L), eq(FRANJA_VALIDA), eq(EstadoCita.PROGRAMADA))).willReturn(true);

            assertThatThrownBy(() -> validador.validar(unMedico(), unPaciente(null), FRANJA_VALIDA))
                    .isInstanceOf(ConflictoDeNegocioException.class)
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.MEDICO_NO_DISPONIBLE);
        }

        @Test
        @DisplayName("RN-04: el paciente ya tiene cita en esa franja, aunque sea con otro médico")
        void pacienteOcupado() {
            sinBloqueo();
            given(citaRepository.existsByMedicoIdAndFechaHoraAndEstado(any(), any(), any()))
                    .willReturn(false);
            given(citaRepository.existsByPacienteIdAndFechaHoraAndEstado(
                    eq(2L), eq(FRANJA_VALIDA), eq(EstadoCita.PROGRAMADA))).willReturn(true);

            assertThatThrownBy(() -> validador.validar(unMedico(), unPaciente(null), FRANJA_VALIDA))
                    .isInstanceOf(ConflictoDeNegocioException.class)
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.PACIENTE_NO_DISPONIBLE);
        }
    }

    // ------------------------------------------------------------------ apoyo

    private void sinBloqueo() {
        given(politicaDePenalizaciones.evaluarBloqueo(any()))
                .willReturn(EstadoDeBloqueo.sinBloqueo(0));
    }

    private void agendasLibres() {
        given(citaRepository.existsByMedicoIdAndFechaHoraAndEstado(any(), any(), any()))
                .willReturn(false);
        given(citaRepository.existsByPacienteIdAndFechaHoraAndEstado(any(), any(), any()))
                .willReturn(false);
    }

    private static CodigoError codigoDe(Runnable accion) {
        try {
            accion.run();
        } catch (ExcepcionDeDominio ex) {
            return ex.getCodigo();
        }
        throw new AssertionError("Se esperaba una excepción de dominio y no se lanzó ninguna.");
    }

    private static Medico unMedico() {
        return Medico.builder()
                .id(1L)
                .nombreCompleto("Dra. María González")
                .especialidad("Cardiología")
                .build();
    }

    private static Paciente unPaciente(LocalDate fechaNacimiento) {
        return Paciente.builder()
                .id(2L)
                .nombreCompleto("Juan Pérez")
                .documentoIdentidad("1020304050")
                .telefono("3001234567")
                .email("juan.perez@example.com")
                .fechaNacimiento(fechaNacimiento)
                .build();
    }
}
