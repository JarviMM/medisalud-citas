package com.medisalud.agenda.repository;

import com.medisalud.agenda.domain.PenalizacionPaciente;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link PenalizacionPaciente}.
 */
@Repository
public interface PenalizacionPacienteRepository extends JpaRepository<PenalizacionPaciente, Long> {

    /**
     * Penalizaciones vigentes de un paciente, de la mas antigua a la mas reciente.
     *
     * <p>Sostiene las dos mitades de la RN-05 con una sola consulta: el tamanio de la
     * lista determina si el paciente esta bloqueado (>= 3) y el primer elemento indica
     * cuando expira la penalizacion mas antigua, es decir, desde cuando podra volver a
     * agendar.</p>
     *
     * @param pacienteId paciente consultado
     * @param desde inicio de la ventana movil (normalmente {@code ahora - 30 dias})
     */
    List<PenalizacionPaciente> findByPacienteIdAndFechaRegistroGreaterThanEqualOrderByFechaRegistroAsc(
            Long pacienteId, LocalDateTime desde);
}
