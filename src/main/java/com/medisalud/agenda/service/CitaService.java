package com.medisalud.agenda.service;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import com.medisalud.agenda.dto.CancelacionResponse;
import com.medisalud.agenda.dto.CitaResponse;
import com.medisalud.agenda.dto.CrearCitaRequest;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.validator.ValidadorDeAgendamiento;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

        validador.validar(medico, paciente, solicitud.fechaHora());

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

        if (!cita.estaProgramada()) {
            throw new ConflictoDeNegocioException(CodigoError.CITA_NO_CANCELABLE,
                    "Solo se pueden cancelar citas programadas; esta cita ya está %s."
                            .formatted(cita.getEstado().name().toLowerCase(Locale.ROOT)));
        }

        LocalDateTime momentoCancelacion = LocalDateTime.now(clock);
        cita.cancelar(momentoCancelacion);

        Optional<PenalizacionPaciente> penalizacion =
                politicaDePenalizaciones.registrarSiLaCancelacionEsTardia(cita, momentoCancelacion);
        EstadoDeBloqueo bloqueo =
                politicaDePenalizaciones.evaluarBloqueo(cita.getPaciente().getId());

        return CancelacionResponse.de(cita, penalizacion, bloqueo);
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
