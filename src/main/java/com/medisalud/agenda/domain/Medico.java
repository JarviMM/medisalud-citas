package com.medisalud.agenda.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Profesional que atiende citas.
 *
 * <p>Las restricciones de formato (longitud del nombre, formato de email, minimo de
 * digitos del telefono) viven en los DTOs de entrada con Bean Validation: la entidad
 * modela la persistencia, no el contrato HTTP. Aqui solo se declaran las restricciones
 * que deben existir a nivel de esquema (obligatoriedad y longitud de columna) como
 * segunda linea de defensa.</p>
 */
@Entity
@Table(name = "medicos")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medico extends BaseEntity {

    @Column(name = "nombre_completo", nullable = false, length = 100)
    private String nombreCompleto;

    @Column(name = "especialidad", nullable = false, length = 80)
    private String especialidad;

    /** Opcional segun el enunciado. */
    @Column(name = "telefono", length = 30)
    private String telefono;

    /** Opcional segun el enunciado. */
    @Column(name = "email", length = 150)
    private String email;
}
