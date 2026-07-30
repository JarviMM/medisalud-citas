package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Paciente;
import java.time.LocalDate;

/**
 * Representacion publica de un paciente.
 */
public record PacienteResponse(
        Long id,
        String nombreCompleto,
        String documentoIdentidad,
        String telefono,
        String email,
        LocalDate fechaNacimiento) {

    public static PacienteResponse desde(Paciente paciente) {
        return new PacienteResponse(
                paciente.getId(),
                paciente.getNombreCompleto(),
                paciente.getDocumentoIdentidad(),
                paciente.getTelefono(),
                paciente.getEmail(),
                paciente.getFechaNacimiento());
    }
}
