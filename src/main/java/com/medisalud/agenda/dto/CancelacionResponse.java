package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import java.time.LocalDateTime;
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
 * <p>{@code motivoPenalizacion} y {@code puedeAgendarDesde} viajan como {@code null} y
 * desaparecen del JSON cuando no aplican.</p>
 *
 * @param cita                   la cita ya cancelada
 * @param penalizacionRegistrada si esta cancelacion genero penalizacion (RN-05)
 * @param motivoPenalizacion     antelacion concreta con la que se cancelo; nulo si no penalizo
 * @param penalizacionesVigentes cancelaciones tardias en los ultimos 30 dias, ya contando esta
 * @param pacienteBloqueado      si a partir de ahora no puede agendar nuevas citas
 * @param puedeAgendarDesde      cuando se le levanta el bloqueo; nulo si no esta bloqueado
 */
public record CancelacionResponse(
        CitaResponse cita,
        boolean penalizacionRegistrada,
        String motivoPenalizacion,
        int penalizacionesVigentes,
        boolean pacienteBloqueado,
        LocalDateTime puedeAgendarDesde) {

    public static CancelacionResponse de(
            Cita cita, Optional<PenalizacionPaciente> penalizacion, EstadoDeBloqueo bloqueo) {
        return new CancelacionResponse(
                CitaResponse.desde(cita),
                penalizacion.isPresent(),
                penalizacion.map(PenalizacionPaciente::getMotivo).orElse(null),
                bloqueo.penalizacionesVigentes(),
                bloqueo.bloqueado(),
                bloqueo.puedeAgendarDesde());
    }
}
