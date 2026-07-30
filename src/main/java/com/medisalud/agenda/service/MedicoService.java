package com.medisalud.agenda.service;

import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.dto.CrearMedicoRequest;
import com.medisalud.agenda.dto.MedicoResponse;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.repository.MedicoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso sobre medicos.
 *
 * <p><b>Por que el servicio recibe y devuelve DTOs</b> en lugar de entidades: con
 * {@code spring.jpa.open-in-view} desactivado, la sesion de JPA se cierra al terminar el
 * metodo transaccional. Si el mapeo a DTO ocurriera en el controller, cualquier acceso a
 * una asociacion perezosa fallaria con {@code LazyInitializationException}. Manteniendo la
 * conversion dentro de la transaccion, el controller recibe datos ya materializados y su
 * unica responsabilidad es traducir HTTP.</p>
 *
 * <p>La clase es {@code readOnly = true} por defecto y solo los metodos que escriben
 * abren una transaccion de lectura-escritura. Una transaccion de solo lectura le ahorra a
 * Hibernate el chequeo de suciedad al cerrar y le permite al driver optimizar.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicoService {

    private static final Sort ORDEN_POR_NOMBRE = Sort.by(Sort.Direction.ASC, "nombreCompleto");

    private final MedicoRepository medicoRepository;

    @Transactional
    public MedicoResponse crear(CrearMedicoRequest solicitud) {
        Medico medico = Medico.builder()
                .nombreCompleto(solicitud.nombreCompleto())
                .especialidad(solicitud.especialidad())
                .telefono(solicitud.telefono())
                .email(solicitud.email())
                .build();
        return MedicoResponse.desde(medicoRepository.save(medico));
    }

    /** Orden estable por nombre: sin {@code ORDER BY} el motor no garantiza el orden. */
    public List<MedicoResponse> listar() {
        return medicoRepository.findAll(ORDEN_POR_NOMBRE).stream()
                .map(MedicoResponse::desde)
                .toList();
    }

    public MedicoResponse obtenerPorId(Long id) {
        return MedicoResponse.desde(buscarEntidad(id));
    }

    /**
     * Recupera la entidad o falla con 404.
     *
     * <p>Pensado para que otros servicios del dominio (el de citas) resuelvan la
     * referencia al medico sin duplicar el manejo del "no existe". No debe usarse desde la
     * capa web: los controllers consumen los metodos que devuelven DTOs.</p>
     */
    public Medico buscarEntidad(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("el médico", id));
    }
}
