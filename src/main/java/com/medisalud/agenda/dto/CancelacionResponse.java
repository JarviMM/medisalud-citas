package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import java.util.Optional;

/**
 * Resultado de cancelar una cita.
 *
 * <p>Devuelve mas que la cita actualizada porque cancelar tiene consecuencias que el
 * paciente necesita conocer <b>en ese momento</b>: si la cancelacion le ha costado una
 * penalizacion, cuantas lleva acumuladas y si acaba de quedarse sin poder agendar. Sin esta
 * informacion, el paciente cancela sin saberlo y se entera al intentar reservar de nuevo,
 * cuando ya no puede hacer nada al respecto.</p>
 *
 * @param cita         la cita ya cancelada
 * @param penalizacion consecuencias de la RN-05 para el paciente
 */
public record CancelacionResponse(CitaResponse cita, ResumenDePenalizacion penalizacion) {

    public static CancelacionResponse de(
            Cita cita, Optional<PenalizacionPaciente> penalizacion, EstadoDeBloqueo bloqueo) {
        return new CancelacionResponse(
                CitaResponse.desde(cita), ResumenDePenalizacion.de(penalizacion, bloqueo));
    }
}
