package com.medisalud.agenda.service;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.FiltroDeCitas;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import com.medisalud.agenda.dto.CancelacionResponse;
import com.medisalud.agenda.dto.CitaResponse;
import com.medisalud.agenda.dto.CrearCitaRequest;
import com.medisalud.agenda.dto.ReprogramacionResponse;
import com.medisalud.agenda.dto.ReprogramarCitaRequest;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.repository.EspecificacionesDeCita;
import com.medisalud.agenda.validator.ValidadorDeAgendamiento;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso sobre citas.
 *
 * <p>El servicio orquesta y no decide: resuelve las referencias, delega las reglas de
 * agendamiento en {@link ValidadorDeAgendamiento} y las de penalizacion en
 * {@link PoliticaDePenalizaciones}, y persiste. Toda la logica de "que es una cita valida"
 * y de "cuando se penaliza" vive fuera de aqui.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CitaService {

    /** Orden estable: cronologico y, a igual instante, por identificador. */
    private static final Sort ORDEN_CRONOLOGICO =
            Sort.by(Sort.Order.asc("fechaHora"), Sort.Order.asc("id"));

    private final CitaRepository citaRepository;
    private final MedicoService medicoService;
    private final PacienteService pacienteService;
    private final ValidadorDeAgendamiento validador;
    private final PoliticaDePenalizaciones politicaDePenalizaciones;
    private final Clock clock;

    /**
     * Reserva una cita aplicando RN-01 a RN-04.
     *
     * <p>Resolver medico y paciente a traves de sus servicios, y no de los repositorios,
     * reutiliza el manejo del "no existe": si cualquiera de los dos identificadores no
     * corresponde a nadie, la peticion termina en 404 sin que esta clase lo programe.</p>
     *
     * <p>El {@code try} alrededor de {@code saveAndFlush} no duplica la validacion previa,
     * la completa: entre validar y escribir cabe otra peticion que reserve la misma franja.
     * El esquema impide la duplicidad, y aqui esa colision se traduce a un 409 con un
     * mensaje que invita a reintentar, en lugar de escapar como error de integridad. El
     * mensaje es comun a las dos restricciones porque en ese punto ya no se puede saber
     * cual de las dos agendas provoco el choque.</p>
     */
    @Transactional
    public CitaResponse reservar(CrearCitaRequest solicitud) {
        Medico medico = medicoService.buscarEntidad(solicitud.medicoId());
        Paciente paciente = pacienteService.buscarEntidad(solicitud.pacienteId());

        validador.validarReserva(medico, paciente, solicitud.fechaHora());

        Cita cita = Cita.builder()
                .medico(medico)
                .paciente(paciente)
                .fechaHora(solicitud.fechaHora())
                .estado(EstadoCita.PROGRAMADA)
                .build();

        try {
            return CitaResponse.desde(citaRepository.saveAndFlush(cita));
        } catch (DataIntegrityViolationException colision) {
            throw new ConflictoDeNegocioException(CodigoError.FRANJA_OCUPADA,
                    "La franja acaba de ser ocupada por otra reserva. Elija otro horario.");
        }
    }

    /**
     * Cancela una cita programada y aplica la RN-05.
     *
     * <p>No hay ninguna llamada a {@code save}: la cita se cargo dentro de esta transaccion,
     * asi que Hibernate detecta el cambio de estado por comprobacion de suciedad y emite el
     * UPDATE al confirmar. Anadir un {@code save} explicito no cambiaria nada salvo sugerir
     * que hace falta.</p>
     *
     * <p>El bloqueo se evalua <b>despues</b> de registrar la penalizacion, para que la
     * respuesta refleje la situacion en la que queda el paciente tras esta cancelacion y no
     * la que tenia antes. Es justo la cancelacion que le deja bloqueado la que necesita
     * avisarle.</p>
     *
     * @throws ConflictoDeNegocioException si la cita ya estaba cancelada o atendida
     */
    @Transactional
    public CancelacionResponse cancelar(Long id) {
        Cita cita = buscarEntidad(id);

        exigirQueEsteProgramada(cita, "cancelar");

        LocalDateTime momentoCancelacion = LocalDateTime.now(clock);
        cita.cancelar(momentoCancelacion);

        Optional<PenalizacionPaciente> penalizacion =
                politicaDePenalizaciones.registrarSiLaCancelacionEsTardia(cita, momentoCancelacion);
        EstadoDeBloqueo bloqueo =
                politicaDePenalizaciones.evaluarBloqueo(cita.getPaciente().getId());

        return CancelacionResponse.de(cita, penalizacion, bloqueo);
    }

    /**
     * Reprograma una cita: cancela la original y crea una nueva en el horario pedido (RN-06).
     *
     * <p><b>Todo o nada.</b> El metodo es transaccional, asi que si el nuevo horario no esta
     * disponible la cancelacion de la original se deshace con el resto y el paciente
     * conserva su cita. Nunca queda una cita cancelada sin sustituta.</p>
     *
     * <p><b>Por que se valida antes de cancelar.</b> Aunque la transaccion ya garantiza la
     * atomicidad, el orden importa por dos motivos concretos:</p>
     * <ol>
     *   <li>Si se cancelara primero, la penalizacion que puede generar <i>esta misma</i>
     *       reprogramacion contaria en la evaluacion del bloqueo, y una tercera cancelacion
     *       tardia podria impedir la operacion que acababa de generarla. La reprogramacion
     *       fallaria por una penalizacion que, al deshacerse la transaccion, tampoco
     *       quedaria registrada. Validando antes, el bloqueo se mide con el historial previo
     *       y la penalizacion afecta a las citas futuras, que es lo razonable.</li>
     *   <li>La cita original sigue ocupando su franja durante la validacion, asi que
     *       reprogramar al mismo horario chocaria consigo misma y devolveria un "el medico ya
     *       tiene una cita en esa franja" enganoso, porque esa cita es la suya. Por eso ese
     *       caso se descarta explicitamente antes, con su propio mensaje.</li>
     * </ol>
     *
     * <p>Descartado el mismo horario, la cita nueva y la original nunca compiten por la misma
     * franja, de modo que el INSERT de la nueva y el UPDATE que libera la anterior pueden
     * salir en cualquier orden dentro del flush sin violar las restricciones de unicidad.</p>
     */
    @Transactional
    public ReprogramacionResponse reprogramar(Long id, ReprogramarCitaRequest solicitud) {
        Cita original = buscarEntidad(id);
        exigirQueEsteProgramada(original, "reprogramar");

        LocalDateTime nuevaFechaHora = solicitud.nuevaFechaHora();
        if (nuevaFechaHora.equals(original.getFechaHora())) {
            throw new SolicitudInvalidaException(CodigoError.REPROGRAMACION_SIN_CAMBIO,
                    "La cita ya está programada para esa fecha y hora.");
        }

        Medico medico = original.getMedico();
        Paciente paciente = original.getPaciente();
        validador.validarReprogramacion(medico, paciente, nuevaFechaHora);

        LocalDateTime ahora = LocalDateTime.now(clock);
        original.cancelar(ahora);
        Optional<PenalizacionPaciente> penalizacion =
                politicaDePenalizaciones.registrarSiLaCancelacionEsTardia(original, ahora);

        Cita nueva = Cita.builder()
                .medico(medico)
                .paciente(paciente)
                .fechaHora(nuevaFechaHora)
                .estado(EstadoCita.PROGRAMADA)
                .citaOrigenId(original.getId())
                .build();

        try {
            citaRepository.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException colision) {
            throw new ConflictoDeNegocioException(CodigoError.FRANJA_OCUPADA,
                    "La franja acaba de ser ocupada por otra reserva. Elija otro horario.");
        }

        EstadoDeBloqueo bloqueo = politicaDePenalizaciones.evaluarBloqueo(paciente.getId());

        return ReprogramacionResponse.de(original, nueva, penalizacion, bloqueo);
    }

    private void exigirQueEsteProgramada(Cita cita, String operacion) {
        if (!cita.estaProgramada()) {
            throw new ConflictoDeNegocioException(CodigoError.CITA_NO_MODIFICABLE,
                    "Solo se pueden %s citas programadas; esta cita ya está %s."
                            .formatted(operacion, cita.getEstado().name().toLowerCase(Locale.ROOT)));
        }
    }

    /**
     * Lista las citas que cumplen los filtros indicados (RF-06).
     *
     * <p>El orden es cronologico y se desempata por identificador. Sin un {@code ORDER BY}
     * explicito el motor no garantiza ningun orden, y dos peticiones identicas podrian
     * devolver las mismas citas en distinta secuencia.</p>
     *
     * <p><b>Limitacion conocida:</b> el listado no esta paginado, porque el enunciado define
     * la respuesta como una lista. Con un volumen real de citas habria que paginarlo; el
     * repositorio ya extiende {@code JpaSpecificationExecutor}, asi que seria cambiar la
     * firma por una que acepte {@code Pageable} sin tocar los filtros.</p>
     */
    public List<CitaResponse> listar(FiltroDeCitas filtro) {
        return citaRepository.findAll(EspecificacionesDeCita.segun(filtro), ORDEN_CRONOLOGICO)
                .stream()
                .map(CitaResponse::desde)
                .toList();
    }

    public CitaResponse obtenerPorId(Long id) {
        return CitaResponse.desde(buscarEntidad(id));
    }

    /** Ver {@link MedicoService#buscarEntidad(Long)}: uso interno del dominio. */
    public Cita buscarEntidad(Long id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("la cita", id));
    }
}
