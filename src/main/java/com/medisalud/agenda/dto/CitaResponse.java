package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoCita;
import java.time.LocalDateTime;

/**
 * Representacion publica de una cita.
 *
 * <p>Incluye los datos minimos del medico y del paciente en lugar de solo sus
 * identificadores: quien consulta una cita casi siempre necesita mostrar de quien es, y
 * devolver solo los ids obligaria al cliente a encadenar dos peticiones mas por cada cita
 * de un listado.</p>
 *
 * <p><b>Aviso sobre carga perezosa:</b> {@link #desde(Cita)} navega a {@code medico} y
 * {@code paciente}, que son asociaciones {@code LAZY}, asi que debe invocarse dentro de la
 * transaccion del servicio. En un listado esto provocaria dos consultas por cita; por eso
 * la consulta del listado declara un {@code @EntityGraph} que trae ambas asociaciones en el
 * mismo SELECT. Cualquier consulta nueva que devuelva varias citas debe hacer lo mismo.</p>
 */
public record CitaResponse(
        Long id,
        MedicoResumen medico,
        PacienteResumen paciente,
        LocalDateTime fechaHora,
        EstadoCita estado,
        LocalDateTime fechaCancelacion,
        Long citaOrigenId) {

    public static CitaResponse desde(Cita cita) {
        return new CitaResponse(
                cita.getId(),
                MedicoResumen.desde(cita.getMedico()),
                PacienteResumen.desde(cita.getPaciente()),
                cita.getFechaHora(),
                cita.getEstado(),
                cita.getFechaCancelacion(),
                cita.getCitaOrigenId());
    }
}
