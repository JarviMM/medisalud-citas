package com.medisalud.agenda.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.Period;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Persona que solicita citas.
 *
 * <p>El documento de identidad es la clave de negocio: se declara {@code UNIQUE} en el
 * esquema para que la unicidad quede garantizada por la base de datos y no dependa
 * unicamente de una comprobacion previa en el servicio, que es susceptible a condiciones
 * de carrera entre peticiones concurrentes.</p>
 */
@Entity
@Table(
        name = "pacientes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paciente_documento_identidad",
                columnNames = "documento_identidad"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Paciente extends BaseEntity {

    @Column(name = "nombre_completo", nullable = false, length = 100)
    private String nombreCompleto;

    @Column(name = "documento_identidad", nullable = false, length = 30)
    private String documentoIdentidad;

    @Column(name = "telefono", nullable = false, length = 30)
    private String telefono;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    /** Opcional segun el enunciado. Ver {@link #edadEn(LocalDate)} para la RN-03. */
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    /**
     * Edad cumplida del paciente en la fecha indicada.
     *
     * <p><b>RN-03:</b> la fecha de nacimiento es opcional. Si no se registro se asume
     * edad 0, que es una edad valida y por tanto no impide agendar. El rechazo de fechas
     * de nacimiento futuras se resuelve en la validacion del DTO de registro (400), de
     * modo que aqui nunca deberia obtenerse una edad negativa.</p>
     *
     * @param referencia fecha contra la que se calcula la edad (normalmente "hoy")
     * @return anios cumplidos, o 0 si no hay fecha de nacimiento registrada
     */
    public int edadEn(LocalDate referencia) {
        if (fechaNacimiento == null) {
            return 0;
        }
        return Period.between(fechaNacimiento, referencia).getYears();
    }
}
