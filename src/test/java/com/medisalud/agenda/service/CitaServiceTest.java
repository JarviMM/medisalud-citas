package com.medisalud.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.dto.CitaResponse;
import com.medisalud.agenda.dto.CrearCitaRequest;
import com.medisalud.agenda.dto.ReprogramacionResponse;
import com.medisalud.agenda.dto.ReprogramarCitaRequest;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.ExcepcionDeDominio;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.validator.ValidadorDeAgendamiento;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Orquestacion de {@link CitaService}.
 *
 * <p>Cubre lo que los tests de integracion no alcanzan: la traduccion de una colision de
 * integridad a 409, y el orden en que el servicio llama a sus colaboradores. Ese orden no es
 * un detalle de implementacion, es una decision de diseno documentada, y sin un test que lo
 * fije se pierde en la primera reordenacion descuidada.</p>
 */
@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 8, 3, 8, 0);
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-08-03T08:00:00Z"), ZoneOffset.UTC);

    private static final LocalDateTime FRANJA = LocalDateTime.of(2026, 8, 3, 15, 0);
    private static final LocalDateTime OTRA_FRANJA = LocalDateTime.of(2026, 8, 3, 16, 0);

    @Mock private CitaRepository citaRepository;
    @Mock private MedicoService medicoService;
    @Mock private PacienteService pacienteService;
    @Mock private ValidadorDeAgendamiento validador;
    @Mock private PoliticaDePenalizaciones politicaDePenalizaciones;

    private CitaService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new CitaService(citaRepository, medicoService, pacienteService,
                validador, politicaDePenalizaciones, RELOJ);
    }

    @Nested
    @DisplayName("Reservar")
    class Reservar {

        @Test
        @DisplayName("Crea la cita en estado PROGRAMADA con el médico y el paciente resueltos")
        void reservaCorrecta() {
            resuelveMedicoYPaciente();
            given(citaRepository.saveAndFlush(any())).willAnswer(i -> i.getArgument(0));

            CitaResponse respuesta = servicio.reservar(new CrearCitaRequest(1L, 2L, FRANJA));

            assertThat(respuesta.estado()).isEqualTo(EstadoCita.PROGRAMADA);
            assertThat(respuesta.fechaHora()).isEqualTo(FRANJA);
            assertThat(respuesta.medico().id()).isEqualTo(1L);
            assertThat(respuesta.paciente().id()).isEqualTo(2L);
            assertThat(respuesta.citaOrigenId()).isNull();
        }

        @Test
        @DisplayName("Si una regla falla, no se escribe nada")
        void reglaIncumplidaNoPersiste() {
            resuelveMedicoYPaciente();
            willThrow(new ConflictoDeNegocioException(
                    CodigoError.MEDICO_NO_DISPONIBLE, "ocupado"))
                    .given(validador).validarReserva(any(), any(), any());

            assertThatThrownBy(() -> servicio.reservar(new CrearCitaRequest(1L, 2L, FRANJA)))
                    .isInstanceOf(ConflictoDeNegocioException.class);

            verify(citaRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Una colisión concurrente en el INSERT se traduce a 409, no escapa como error de integridad")
        void colisionConcurrente() {
            resuelveMedicoYPaciente();
            // Otra petición ganó la carrera entre la validación y la escritura: la
            // restricción única del esquema es la que la detiene.
            given(citaRepository.saveAndFlush(any()))
                    .willThrow(new DataIntegrityViolationException("uk_cita_medico_franja"));

            assertThatThrownBy(() -> servicio.reservar(new CrearCitaRequest(1L, 2L, FRANJA)))
                    .isInstanceOf(ConflictoDeNegocioException.class)
                    .hasMessageContaining("Elija otro horario")
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.FRANJA_OCUPADA);
        }
    }

    @Nested
    @DisplayName("Cancelar")
    class Cancelar {

        @Test
        @DisplayName("Una cita ya cancelada no se puede volver a cancelar")
        void citaYaCancelada() {
            Cita cita = unaCita(FRANJA);
            cita.cancelar(AHORA.minusHours(1));
            given(citaRepository.findById(5L)).willReturn(Optional.of(cita));

            assertThatThrownBy(() -> servicio.cancelar(5L))
                    .isInstanceOf(ConflictoDeNegocioException.class)
                    .hasMessageContaining("ya está cancelada")
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.CITA_NO_MODIFICABLE);
        }

        @Test
        @DisplayName("El bloqueo se evalúa después de registrar la penalización de esta cancelación")
        void evaluaElBloqueoDespuesDePenalizar() {
            Cita cita = unaCita(FRANJA);
            given(citaRepository.findById(5L)).willReturn(Optional.of(cita));
            given(politicaDePenalizaciones.registrarSiLaCancelacionEsTardia(any(), any()))
                    .willReturn(Optional.empty());
            given(politicaDePenalizaciones.evaluarBloqueo(2L))
                    .willReturn(EstadoDeBloqueo.sinBloqueo(0));

            servicio.cancelar(5L);

            InOrder orden = inOrder(politicaDePenalizaciones);
            orden.verify(politicaDePenalizaciones)
                    .registrarSiLaCancelacionEsTardia(cita, AHORA);
            orden.verify(politicaDePenalizaciones).evaluarBloqueo(2L);
        }
    }

    @Nested
    @DisplayName("Reprogramar")
    class Reprogramar {

        @Test
        @DisplayName("Enlaza la cita nueva con la anterior y deja la anterior cancelada")
        void reprogramacionCorrecta() {
            Cita original = unaCita(FRANJA);
            given(citaRepository.findById(5L)).willReturn(Optional.of(original));
            given(citaRepository.saveAndFlush(any())).willAnswer(i -> i.getArgument(0));
            given(politicaDePenalizaciones.registrarSiLaCancelacionEsTardia(any(), any()))
                    .willReturn(Optional.empty());
            given(politicaDePenalizaciones.evaluarBloqueo(2L))
                    .willReturn(EstadoDeBloqueo.sinBloqueo(0));

            ReprogramacionResponse respuesta =
                    servicio.reprogramar(5L, new ReprogramarCitaRequest(OTRA_FRANJA));

            assertThat(respuesta.citaAnterior().estado()).isEqualTo(EstadoCita.CANCELADA);
            assertThat(respuesta.citaAnterior().fechaCancelacion()).isEqualTo(AHORA);
            assertThat(respuesta.citaNueva().estado()).isEqualTo(EstadoCita.PROGRAMADA);
            assertThat(respuesta.citaNueva().fechaHora()).isEqualTo(OTRA_FRANJA);
            assertThat(respuesta.citaNueva().citaOrigenId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("Reprogramar al mismo horario se rechaza antes de tocar nada")
        void mismoHorario() {
            given(citaRepository.findById(5L)).willReturn(Optional.of(unaCita(FRANJA)));

            assertThatThrownBy(() ->
                    servicio.reprogramar(5L, new ReprogramarCitaRequest(FRANJA)))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.REPROGRAMACION_SIN_CAMBIO);

            verify(validador, never()).validarReprogramacion(any(), any(), any());
            verify(citaRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Valida el nuevo horario ANTES de cancelar la cita original")
        void validaAntesDeCancelar() {
            Cita original = unaCita(FRANJA);
            given(citaRepository.findById(5L)).willReturn(Optional.of(original));
            given(citaRepository.saveAndFlush(any())).willAnswer(i -> i.getArgument(0));
            given(politicaDePenalizaciones.registrarSiLaCancelacionEsTardia(any(), any()))
                    .willReturn(Optional.empty());
            given(politicaDePenalizaciones.evaluarBloqueo(2L))
                    .willReturn(EstadoDeBloqueo.sinBloqueo(0));

            servicio.reprogramar(5L, new ReprogramarCitaRequest(OTRA_FRANJA));

            // Si se invirtiera el orden, la penalización que genera esta misma
            // reprogramación contaría al evaluar el bloqueo y podría impedirla.
            InOrder orden = inOrder(validador, politicaDePenalizaciones);
            orden.verify(validador).validarReprogramacion(any(), any(), any());
            orden.verify(politicaDePenalizaciones)
                    .registrarSiLaCancelacionEsTardia(any(), any());
        }

        @Test
        @DisplayName("Si el nuevo horario colisiona en el INSERT, se traduce a 409")
        void colisionEnElNuevoHorario() {
            given(citaRepository.findById(5L)).willReturn(Optional.of(unaCita(FRANJA)));
            given(politicaDePenalizaciones.registrarSiLaCancelacionEsTardia(any(), any()))
                    .willReturn(Optional.empty());
            given(citaRepository.saveAndFlush(any()))
                    .willThrow(new DataIntegrityViolationException("uk_cita_medico_franja"));

            assertThatThrownBy(() ->
                    servicio.reprogramar(5L, new ReprogramarCitaRequest(OTRA_FRANJA)))
                    .isInstanceOf(ConflictoDeNegocioException.class)
                    .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                    .isEqualTo(CodigoError.FRANJA_OCUPADA);
        }
    }

    // ------------------------------------------------------------------ apoyo

    private void resuelveMedicoYPaciente() {
        given(medicoService.buscarEntidad(1L)).willReturn(unMedico());
        given(pacienteService.buscarEntidad(2L)).willReturn(unPaciente());
    }

    private static Cita unaCita(LocalDateTime fechaHora) {
        return Cita.builder()
                .id(5L)
                .medico(unMedico())
                .paciente(unPaciente())
                .fechaHora(fechaHora)
                .estado(EstadoCita.PROGRAMADA)
                .build();
    }

    private static Medico unMedico() {
        return Medico.builder()
                .id(1L)
                .nombreCompleto("Dra. María González")
                .especialidad("Cardiología")
                .build();
    }

    private static Paciente unPaciente() {
        return Paciente.builder()
                .id(2L)
                .nombreCompleto("Juan Pérez")
                .documentoIdentidad("1020304050")
                .telefono("3001234567")
                .email("juan.perez@example.com")
                .build();
    }
}
