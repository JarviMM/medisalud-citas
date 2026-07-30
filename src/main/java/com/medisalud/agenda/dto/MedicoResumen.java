package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Medico;

/**
 * Datos minimos de un medico para incrustar en otras respuestas.
 *
 * <p>Se extrajo de {@code CitaResponse} al necesitarlo tambien la respuesta de
 * disponibilidad: mantener dos records identicos habria obligado a cambiar los dos cada vez
 * que se anada un campo. No se reutiliza {@code MedicoResponse} porque ese es el recurso
 * completo, con telefono y email, y quien consulta una cita o unas franjas libres no
 * necesita los datos de contacto del profesional.</p>
 */
public record MedicoResumen(Long id, String nombreCompleto, String especialidad) {

    public static MedicoResumen desde(Medico medico) {
        return new MedicoResumen(
                medico.getId(), medico.getNombreCompleto(), medico.getEspecialidad());
    }
}
