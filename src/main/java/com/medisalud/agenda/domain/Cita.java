package com.medisalud.agenda.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Reserva de una franja de 30 minutos entre un paciente y un medico.
 *
 * <p>Las asociaciones son {@code LAZY} de forma explicita: el valor por defecto de
 * {@code @ManyToOne} es {@code EAGER} y provocaria consultas adicionales en cada listado
 * de citas. El mapeo a DTO se hace dentro de la transaccion del servicio, por lo que la
 * carga perezosa no genera {@code LazyInitializationException} (ademas
 * {@code spring.jpa.open-in-view} esta desactivado a proposito).</p>
 *
 * <h2>Unicidad de franja garantizada por la base de datos (RN-02 y RN-04)</h2>
 *
 * <p>Comprobar la disponibilidad con un {@code exists} previo a la insercion deja una
 * ventana de carrera: dos peticiones simultaneas pueden leer "libre" y ambas insertar. La
 * defensa correcta es un indice unico parcial
 * ({@code UNIQUE (medico_id, fecha_hora) WHERE estado = 'PROGRAMADA'}), pero H2 no
 * soporta indices parciales y JPA no permite declararlos de forma portable.</p>
 *
 * <p>La solucion aplicada usa el comportamiento estandar de SQL: una restriccion
 * {@code UNIQUE} solo se viola cuando <b>todas</b> las columnas de la clave son no nulas e
 * iguales. Por eso la clave incluye {@link #franjaActiva}, una marca que vale
 * {@code TRUE} mientras la cita esta PROGRAMADA y {@code null} en cualquier otro estado:
 * las citas vigentes compiten entre si por la franja, mientras que las canceladas y las
 * atendidas quedan fuera de la restriccion y pueden acumularse sin limite sobre el mismo
 * horario. Es exactamente la semantica de un indice parcial, y funciona igual en H2 y en
 * PostgreSQL sin SQL especifico del motor.</p>
 *
 * <p>El servicio sigue comprobando la disponibilidad antes de insertar, porque asi puede
 * devolver un 409 con un mensaje util; la restriccion es la red de seguridad que cubre la
 * ventana de carrera que esa comprobacion no puede cerrar.</p>
 *
 * <p><b>Nota sobre el orden del flush:</b> Hibernate ejecuta los INSERT antes que los UPDATE
 * dentro de un mismo flush. Una reprogramacion que cancelase la cita original y creara la
 * nueva <i>sobre la misma franja</i> chocaria con estas restricciones, porque el INSERT que
 * reclama el horario saldria antes que el UPDATE que lo libera, y haria falta un
 * {@code flush()} explicito entre ambos. El servicio evita esa situacion de raiz:
 * reprogramar a la fecha y hora que la cita ya tenia se rechaza antes de tocar nada, asi que
 * la cita nueva y la anterior nunca compiten por la misma franja.</p>
 *
 * <p>No se declaran indices adicionales sobre {@code (medico_id, fecha_hora)} ni
 * {@code (paciente_id, fecha_hora)}: las restricciones unicas ya crean indices con esas
 * mismas columnas como prefijo, que es lo que necesitan las consultas de solapamiento y
 * el calculo de disponibilidad (RF-04). Duplicarlos solo anadiria coste de escritura.</p>
 */
@Entity
@Table(
        name = "citas",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cita_medico_franja",
                        columnNames = {"medico_id", "fecha_hora", "franja_activa"}),
                @UniqueConstraint(
                        name = "uk_cita_paciente_franja",
                        columnNames = {"paciente_id", "fecha_hora", "franja_activa"})
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cita extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "paciente_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cita_paciente"))
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "medico_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cita_medico"))
    private Medico medico;

    /** Inicio de la franja. Siempre alineado a :00 o :30 (RN-01). */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCita estado;

    /** Momento en que se cancelo. Nulo mientras la cita no este cancelada. */
    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    /**
     * Cita de la que proviene esta, cuando nace de una reprogramacion (RN-06).
     *
     * <p>Se guarda como identificador suelto y no como asociacion para no crear un ciclo
     * de dependencias dentro de la misma tabla ni arrastrar la cita anterior en cada
     * carga; su unico uso es de trazabilidad/auditoria.</p>
     */
    @Column(name = "cita_origen_id")
    private Long citaOrigenId;

    /**
     * Marca de ocupacion de franja: {@code TRUE} si la cita esta PROGRAMADA, {@code null}
     * en cualquier otro estado.
     *
     * <p>Es estado derivado de {@link #estado} y forma parte de las dos restricciones
     * unicas de la tabla. Deliberadamente no tiene getter ni setter publicos: su unica
     * autoridad es {@link #sincronizarMarcaDeFranja()}, que se ejecuta antes de cada
     * INSERT y de cada UPDATE, de modo que no puede quedar desincronizada del estado.</p>
     */
    @Column(name = "franja_activa")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean franjaActiva;

    /** Solo las citas programadas ocupan franja (RN-02, RN-04) y son cancelables. */
    public boolean estaProgramada() {
        return estado == EstadoCita.PROGRAMADA;
    }

    /**
     * Aplica la transicion a {@link EstadoCita#CANCELADA} registrando el momento exacto.
     *
     * <p>La comprobacion de si la transicion es legal y el calculo de la penalizacion
     * (RN-05) corresponden al servicio: la entidad solo garantiza que estado y fecha de
     * cancelacion se actualicen siempre juntos.</p>
     *
     * @param momentoCancelacion instante en que se solicita la cancelacion
     */
    public void cancelar(LocalDateTime momentoCancelacion) {
        this.estado = EstadoCita.CANCELADA;
        this.fechaCancelacion = momentoCancelacion;
    }

    /** Deriva {@link #franjaActiva} del estado antes de escribir en la base de datos. */
    @PrePersist
    @PreUpdate
    private void sincronizarMarcaDeFranja() {
        this.franjaActiva = estaProgramada() ? Boolean.TRUE : null;
    }
}
