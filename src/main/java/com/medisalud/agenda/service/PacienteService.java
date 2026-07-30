package com.medisalud.agenda.service;

import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.dto.CrearPacienteRequest;
import com.medisalud.agenda.dto.PacienteResponse;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.repository.PacienteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso sobre pacientes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PacienteService {

    private static final Sort ORDEN_POR_NOMBRE = Sort.by(Sort.Direction.ASC, "nombreCompleto");

    private static final String MENSAJE_DOCUMENTO_DUPLICADO =
            "Ya existe un paciente registrado con ese documento de identidad.";

    private final PacienteRepository pacienteRepository;

    /**
     * Registra un paciente garantizando la unicidad del documento de identidad.
     *
     * <p>La comprobacion previa existe para poder responder con un mensaje concreto, pero
     * no basta: entre el {@code SELECT} y el {@code INSERT} otra peticion puede insertar
     * el mismo documento. Por eso se escribe con {@code saveAndFlush} dentro de un
     * {@code try}: con {@code save} a secas el INSERT se aplazaria hasta el commit, es
     * decir, fuera del bloque, y la excepcion escaparia sin traducir. Forzando el flush
     * aqui, la colision se convierte igualmente en un 409 con el mismo mensaje que habria
     * dado la comprobacion previa.</p>
     *
     * <p>El mensaje nunca incluye el documento recibido: es un dato personal y repetirlo
     * en la respuesta permitiria comprobar desde fuera si una persona esta registrada.</p>
     */
    @Transactional
    public PacienteResponse crear(CrearPacienteRequest solicitud) {
        if (pacienteRepository.existsByDocumentoIdentidad(solicitud.documentoIdentidad())) {
            throw new ConflictoDeNegocioException(
                    CodigoError.DOCUMENTO_DUPLICADO, MENSAJE_DOCUMENTO_DUPLICADO);
        }

        Paciente paciente = Paciente.builder()
                .nombreCompleto(solicitud.nombreCompleto())
                .documentoIdentidad(solicitud.documentoIdentidad())
                .telefono(solicitud.telefono())
                .email(solicitud.email())
                .fechaNacimiento(solicitud.fechaNacimiento())
                .build();

        try {
            return PacienteResponse.desde(pacienteRepository.saveAndFlush(paciente));
        } catch (DataIntegrityViolationException colision) {
            throw new ConflictoDeNegocioException(
                    CodigoError.DOCUMENTO_DUPLICADO, MENSAJE_DOCUMENTO_DUPLICADO);
        }
    }

    public List<PacienteResponse> listar() {
        return pacienteRepository.findAll(ORDEN_POR_NOMBRE).stream()
                .map(PacienteResponse::desde)
                .toList();
    }

    public PacienteResponse obtenerPorId(Long id) {
        return PacienteResponse.desde(buscarEntidad(id));
    }

    /** Ver {@link MedicoService#buscarEntidad(Long)}: uso interno del dominio, no de la capa web. */
    public Paciente buscarEntidad(Long id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("el paciente", id));
    }
}
