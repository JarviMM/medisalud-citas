package com.medisalud.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import com.medisalud.agenda.repository.PenalizacionPacienteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reglas de penalizacion y bloqueo (RN-05).
 *
 * <p>Todo el test depende de un reloj fijo: el limite de las 2 horas y la ventana de 30
 * dias solo se pueden comprobar en sus bordes exactos si "ahora" es un valor conocido.</p>
 */
@ExtendWith(MockitoExtension.class)
class PoliticaDePenalizacionesTest {

    /** Lunes 3 de agosto de 2026 a las 10:00. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 8, 3, 10, 0);

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC);

    private static final long PACIENTE_ID = 2L;

    @Mock private PenalizacionPacienteRepository penalizacionRepository;

    private PoliticaDePenalizaciones politica;

    @BeforeEach
    void prepararPolitica() {
        politica = new PoliticaDePenalizaciones(penalizacionRepository, RELOJ);
    }

    @Nested
    @DisplayName("Cuándo se penaliza una cancelación")
    class CuandoSePenaliza {

        @Test
        @DisplayName("Cancelar con 3 horas de antelación no penaliza")
        void conMargenSuficiente() {
            Optional<PenalizacionPaciente> resultado =
                    politica.registrarSiLaCancelacionEsTardia(citaA(AHORA.plusHours(3)), AHORA);

            assertThat(resultado).isEmpty();
            verify(penalizacionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cancelar con exactamente 2 horas NO penaliza: el enunciado dice \"menos de 2 horas\"")
        void enElLimiteExacto() {
            assertThat(politica.registrarSiLaCancelacionEsTardia(citaA(AHORA.plusHours(2)), AHORA))
                    .isEmpty();
        }

        @Test
        @DisplayName("Cancelar un minuto por debajo del límite sí penaliza")
        void justoPorDebajoDelLimite() {
            devuelveLoQueSeGuarda();

            Optional<PenalizacionPaciente> resultado = politica.registrarSiLaCancelacionEsTardia(
                    citaA(AHORA.plusHours(1).plusMinutes(59)), AHORA);

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getFechaRegistro()).isEqualTo(AHORA);
            assertThat(resultado.get().getMotivo()).isEqualTo("Cancelación con 1 h 59 min de antelación.");
        }

        @Test
        @DisplayName("Cancelar 45 minutos antes penaliza y deja constancia del margen")
        void cancelacionTardia() {
            devuelveLoQueSeGuarda();

            Optional<PenalizacionPaciente> resultado =
                    politica.registrarSiLaCancelacionEsTardia(citaA(AHORA.plusMinutes(45)), AHORA);

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getMotivo()).isEqualTo("Cancelación con 45 min de antelación.");
        }

        @Test
        @DisplayName("Cancelar después de la hora de la cita también penaliza")
        void cancelacionPosteriorALaCita() {
            devuelveLoQueSeGuarda();

            Optional<PenalizacionPaciente> resultado =
                    politica.registrarSiLaCancelacionEsTardia(citaA(AHORA.minusHours(1)), AHORA);

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getMotivo()).isEqualTo("Cancelación posterior a la hora de la cita.");
        }
    }

    @Nested
    @DisplayName("Cuándo queda bloqueado el paciente")
    class CuandoSeBloquea {

        @Test
        @DisplayName("Sin penalizaciones no hay bloqueo")
        void sinPenalizaciones() {
            given(penalizacionRepository.buscarFechasVigentes(any(), any())).willReturn(List.of());

            EstadoDeBloqueo estado = politica.evaluarBloqueo(PACIENTE_ID);

            assertThat(estado.bloqueado()).isFalse();
            assertThat(estado.penalizacionesVigentes()).isZero();
            assertThat(estado.puedeAgendarDesde()).isNull();
        }

        @Test
        @DisplayName("Dos penalizaciones vigentes todavía no bloquean")
        void dosPenalizaciones() {
            given(penalizacionRepository.buscarFechasVigentes(any(), any()))
                    .willReturn(List.of(AHORA.minusDays(10), AHORA.minusDays(3)));

            assertThat(politica.evaluarBloqueo(PACIENTE_ID).bloqueado()).isFalse();
        }

        @Test
        @DisplayName("Tres penalizaciones bloquean hasta que expira la más antigua")
        void tresPenalizaciones() {
            LocalDateTime masAntigua = AHORA.minusDays(20);
            given(penalizacionRepository.buscarFechasVigentes(any(), any()))
                    .willReturn(List.of(masAntigua, AHORA.minusDays(10), AHORA.minusDays(1)));

            EstadoDeBloqueo estado = politica.evaluarBloqueo(PACIENTE_ID);

            assertThat(estado.bloqueado()).isTrue();
            assertThat(estado.penalizacionesVigentes()).isEqualTo(3);
            assertThat(estado.puedeAgendarDesde()).isEqualTo(masAntigua.plusDays(30));
        }

        @Test
        @DisplayName("Con cinco penalizaciones el desbloqueo llega cuando expira la tercera, no la primera")
        void cincoPenalizaciones() {
            LocalDateTime tercera = AHORA.minusDays(20);
            given(penalizacionRepository.buscarFechasVigentes(any(), any())).willReturn(List.of(
                    AHORA.minusDays(28),
                    AHORA.minusDays(25),
                    tercera,
                    AHORA.minusDays(10),
                    AHORA.minusDays(1)));

            EstadoDeBloqueo estado = politica.evaluarBloqueo(PACIENTE_ID);

            assertThat(estado.penalizacionesVigentes()).isEqualTo(5);
            // Cuando expiren las dos primeras aún quedarían tres vigentes: sigue bloqueado.
            // El bloqueo cae al expirar la tercera, que es cuando solo quedan dos.
            assertThat(estado.puedeAgendarDesde()).isEqualTo(tercera.plusDays(30));
        }

        @Test
        @DisplayName("La ventana móvil se calcula desde ahora, no desde el mes natural")
        void ventanaMovilDesdeAhora() {
            given(penalizacionRepository.buscarFechasVigentes(any(), any())).willReturn(List.of());

            politica.evaluarBloqueo(PACIENTE_ID);

            verify(penalizacionRepository).buscarFechasVigentes(PACIENTE_ID, AHORA.minusDays(30));
        }
    }

    @Nested
    @DisplayName("Mensaje del bloqueo")
    class MensajeDelBloqueo {

        @Test
        @DisplayName("Indica cuántas lleva y desde cuándo podrá agendar")
        void mensajeCompleto() {
            EstadoDeBloqueo estado =
                    EstadoDeBloqueo.conBloqueo(3, LocalDateTime.of(2026, 8, 15, 14, 30));

            assertThat(politica.describirBloqueo(estado)).isEqualTo(
                    "El paciente acumula 3 cancelaciones tardías en los últimos 30 días "
                            + "y no puede agendar nuevas citas hasta el 15/08/2026 a las 14:30.");
        }
    }

    // ------------------------------------------------------------------ apoyo

    private void devuelveLoQueSeGuarda() {
        given(penalizacionRepository.save(any()))
                .willAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static Cita citaA(LocalDateTime fechaHora) {
        return Cita.builder()
                .id(7L)
                .medico(Medico.builder().id(1L).nombreCompleto("Dra. María González")
                        .especialidad("Cardiología").build())
                .paciente(Paciente.builder().id(PACIENTE_ID).nombreCompleto("Juan Pérez")
                        .documentoIdentidad("1020304050").telefono("3001234567")
                        .email("juan.perez@example.com").build())
                .fechaHora(fechaHora)
                .estado(EstadoCita.PROGRAMADA)
                .build();
    }
}
