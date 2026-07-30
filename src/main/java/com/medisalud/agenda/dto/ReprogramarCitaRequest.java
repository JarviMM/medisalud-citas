package com.medisalud.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Datos de entrada para reprogramar una cita.
 *
 * <p>Solo viaja la nueva fecha y hora: el medico y el paciente no cambian al reprogramar.
 * Cambiar de medico es cancelar y reservar de nuevo, una operacion distinta con otras
 * consecuencias, y admitirla aqui haria que un mismo endpoint significara dos cosas.</p>
 *
 * <p>El nombre del campo es {@code nuevaFechaHora} y no {@code fechaHora} a proposito: en
 * una peticion parcial sobre una cita que ya tiene fecha, el prefijo deja claro cual de las
 * dos es sin necesidad de consultar la documentacion.</p>
 */
public record ReprogramarCitaRequest(

        @Schema(
                example = "2026-08-03T11:00:00",
                description = "Debe ser distinta de la que la cita ya tiene.")
        @NotNull(message = "La nueva fecha y hora de la cita son obligatorias.")
        LocalDateTime nuevaFechaHora) {
}
