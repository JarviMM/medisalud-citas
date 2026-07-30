package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import java.util.Optional;

/**
 * Resultado de reprogramar una cita.
 *
 * <p>Devuelve las <b>dos</b> citas porque reprogramar no modifica una cita, sino que cierra
 * una y abre otra (RN-06). El cliente necesita el identificador de la nueva para operar
 * sobre ella, y el estado final de la anterior para reflejar en su interfaz que quedo
 * cancelada y no simplemente movida.</p>
 *
 * @param citaAnterior la cita original, ya cancelada
 * @param citaNueva    la cita creada, cuyo {@code citaOrigenId} apunta a la anterior
 * @param penalizacion consecuencias de la RN-05: reprogramar tarde tambien penaliza
 */
public record ReprogramacionResponse(
        CitaResponse citaAnterior,
        CitaResponse citaNueva,
        ResumenDePenalizacion penalizacion) {

    public static ReprogramacionResponse de(
            Cita anterior,
            Cita nueva,
            Optional<PenalizacionPaciente> penalizacion,
            EstadoDeBloqueo bloqueo) {
        return new ReprogramacionResponse(
                CitaResponse.desde(anterior),
                CitaResponse.desde(nueva),
                ResumenDePenalizacion.de(penalizacion, bloqueo));
    }
}
