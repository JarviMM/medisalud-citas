package com.medisalud.agenda.service;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.dto.CitaResponse;
import com.medisalud.agenda.dto.CrearCitaRequest;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.validator.ValidadorDeAgendamiento;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso sobre citas.
 *
 * <p>El servicio orquesta y no decide: resuelve las referencias, delega las reglas en
 * {@link ValidadorDeAgendamiento} y persiste. Toda la logica de "que es una cita valida"
 * esta en el validador, de modo que anadir una regla en un paso posterior (el bloqueo por
 * penalizaciones de la RN-05) no toca esta clase.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CitaService {

    private final CitaRepository citaRepository;
    private final MedicoService medicoService;
    private final PacienteService pacienteService;
    private final ValidadorDeAgendamiento validador;

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

    public CitaResponse obtenerPorId(Long id) {
        return CitaResponse.desde(buscarEntidad(id));
    }

    /** Ver {@link MedicoService#buscarEntidad(Long)}: uso interno del dominio. */
    public Cita buscarEntidad(Long id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("la cita", id));
    }
}
