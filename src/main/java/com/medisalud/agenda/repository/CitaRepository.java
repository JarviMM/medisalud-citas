package com.medisalud.agenda.repository;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoCita;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos de {@link Cita}.
 *
 * <p>Todas las consultas son metodos derivados o {@code Specification}: Spring Data las
 * traduce a JPQL con parametros ligados, de forma que nunca se concatena entrada del
 * usuario dentro de una consulta.</p>
 *
 * <p>Se extiende {@link JpaSpecificationExecutor} porque el listado con filtros
 * opcionales (RF-06) combina hasta cinco criterios independientes; resolverlo con
 * metodos derivados exigiria una explosion combinatoria de firmas.</p>
 */
@Repository
public interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {

    /**
     * Listado filtrado de citas, con el medico y el paciente ya cargados (RF-06).
     *
     * <p>Se redeclara el metodo heredado de {@link JpaSpecificationExecutor} solo para
     * anotarlo: sin el grafo, mapear cada cita a su DTO navegaria a dos asociaciones
     * perezosas y lanzaria dos consultas <i>por cita</i>. Un listado de cincuenta citas
     * pasaria de una consulta a ciento una. Con {@code @EntityGraph}, Hibernate resuelve las
     * tres tablas en un unico SELECT con dos JOIN.</p>
     *
     * <p>Es la contrapartida de haber declarado las asociaciones {@code LAZY}: por defecto
     * no se carga nada, y cada consulta declara explicitamente lo que necesita.</p>
     */
    @Override
    @EntityGraph(attributePaths = {"medico", "paciente"})
    List<Cita> findAll(Specification<Cita> especificacion, Sort orden);

    /** RN-02: el medico ya tiene una cita vigente en esa franja exacta. */
    boolean existsByMedicoIdAndFechaHoraAndEstado(
            Long medicoId, LocalDateTime fechaHora, EstadoCita estado);

    /** RN-04: el paciente ya tiene una cita vigente en esa franja, con cualquier medico. */
    boolean existsByPacienteIdAndFechaHoraAndEstado(
            Long pacienteId, LocalDateTime fechaHora, EstadoCita estado);

    /**
     * Horas ya ocupadas por un medico dentro de un rango, para descartarlas del calculo de
     * disponibilidad (RF-04).
     *
     * <p>Devuelve solo la columna {@code fecha_hora} y no entidades completas: el calculo
     * unicamente necesita saber que instantes estan tomados, y materializar cada
     * {@code Cita} con sus asociaciones para despues descartarlas seria trabajo perdido en
     * la consulta mas frecuente del sistema.</p>
     *
     * <p>El rango se interpreta como {@code [desde, hasta)}: el limite superior es
     * exclusivo para que el ultimo instante de un dia no se solape con el primero del
     * siguiente. Los parametros van ligados por nombre, nunca concatenados en el JPQL.</p>
     */
    @Query("""
            select c.fechaHora
            from Cita c
            where c.medico.id = :medicoId
              and c.estado = :estado
              and c.fechaHora >= :desde
              and c.fechaHora < :hasta
            """)
    List<LocalDateTime> buscarHorasOcupadas(
            @Param("medicoId") Long medicoId,
            @Param("estado") EstadoCita estado,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
