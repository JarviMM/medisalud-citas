package com.medisalud.agenda.repository;

import com.medisalud.agenda.domain.Paciente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link Paciente}.
 */
@Repository
public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /** Comprobacion previa de unicidad para devolver 409 con un mensaje util al cliente. */
    boolean existsByDocumentoIdentidad(String documentoIdentidad);

    Optional<Paciente> findByDocumentoIdentidad(String documentoIdentidad);
}
