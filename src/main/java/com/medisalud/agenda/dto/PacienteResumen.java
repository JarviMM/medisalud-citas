package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Paciente;

/**
 * Datos minimos de un paciente para incrustar en otras respuestas.
 *
 * <p>Incluye el documento de identidad porque es como el personal de recepcion identifica
 * a quien tiene delante; se omiten telefono, email y fecha de nacimiento, que no hacen
 * falta para presentar una cita y son datos personales que no conviene repartir por toda
 * la API.</p>
 */
public record PacienteResumen(Long id, String nombreCompleto, String documentoIdentidad) {

    public static PacienteResumen desde(Paciente paciente) {
        return new PacienteResumen(
                paciente.getId(), paciente.getNombreCompleto(), paciente.getDocumentoIdentidad());
    }
}
