package com.medisalud.agenda.repository;

import com.medisalud.agenda.domain.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link Medico}.
 *
 * <p>El CRUD heredado de {@link JpaRepository} cubre por completo los endpoints de
 * medico, por lo que no se anaden metodos derivados que no tengan uso real.</p>
 */
@Repository
public interface MedicoRepository extends JpaRepository<Medico, Long> {
}
