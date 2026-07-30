package com.medisalud.agenda.repository;

import com.medisalud.agenda.domain.PenalizacionPaciente;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link PenalizacionPaciente}.
 */
@Repository
public interface PenalizacionPacienteRepository extends JpaRepository<PenalizacionPaciente, Long> {

    /**
     * Fechas de las penalizaciones vigentes de un paciente, de la mas antigua a la mas
     * reciente.
     *
     * <p>Sostiene las dos mitades de la RN-05 con una sola consulta: el tamano de la lista
     * dice si el paciente esta bloqueado, y su contenido permite calcular en que momento
     * dejara de estarlo.</p>
     *
     * <p>El limite es <b>estrictamente mayor</b> y no "mayor o igual" a proposito: una
     * penalizacion deja de contar exactamente 30 dias despues de registrarse. Con
     * {@code >=}, una penalizacion de justo 30 dias seguiria contando en el instante en que
     * la API acaba de prometer al paciente que ya puede agendar.</p>
     *
     * <p>Devuelve solo la columna de fecha: para decidir el bloqueo no hace falta
     * materializar cada penalizacion con su paciente y su cita.</p>
     *
     * @param pacienteId paciente consultado
     * @param desde      inicio de la ventana movil, exclusive (normalmente {@code ahora - 30 dias})
     */
    @Query("""
            select p.fechaRegistro
            from PenalizacionPaciente p
            where p.paciente.id = :pacienteId
              and p.fechaRegistro > :desde
            order by p.fechaRegistro asc
            """)
    List<LocalDateTime> buscarFechasVigentes(
            @Param("pacienteId") Long pacienteId,
            @Param("desde") LocalDateTime desde);
}
