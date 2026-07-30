package com.medisalud.agenda.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Registro de una cancelacion tardia imputada a un paciente (RN-05).
 *
 * <p><b>Por que una tabla de eventos y no un contador en {@code Paciente}:</b> la regla
 * es una ventana movil de 30 dias, no un acumulado historico. Un contador obligaria a un
 * proceso que lo decremente cuando cada penalizacion expira, seria imposible de auditar
 * ("por que esta bloqueado este paciente?") y no permitiria calcular la fecha en la que
 * volvera a poder agendar. Con eventos, el bloqueo es una simple consulta sobre
 * {@code fecha_registro} y la informacion queda trazable frente a un reclamo.</p>
 */
@Entity
@Table(
        name = "penalizaciones_paciente",
        indexes = @Index(
                name = "idx_penalizacion_paciente_fecha",
                columnList = "paciente_id, fecha_registro"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PenalizacionPaciente extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "paciente_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_penalizacion_paciente"))
    private Paciente paciente;

    /** Cita cuya cancelacion tardia origino la penalizacion. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "cita_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_penalizacion_cita"))
    private Cita cita;

    /** Momento en que se impuso. Define la ventana movil de 30 dias. */
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    /** Texto auditable, p. ej. "Cancelacion con 45 minutos de antelacion". */
    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;
}
