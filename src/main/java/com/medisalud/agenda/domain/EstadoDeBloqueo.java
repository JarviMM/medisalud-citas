package com.medisalud.agenda.domain;

import java.time.LocalDateTime;

/**
 * Situacion de un paciente frente a la RN-05 en un instante dado.
 *
 * <p>Se devuelve tanto al cancelar (para avisar al paciente de las consecuencias) como al
 * intentar agendar (para bloquearle). Al ser una foto de un momento concreto y no un estado
 * almacenado, no hay ningun campo que mantener sincronizado en la base de datos: el bloqueo
 * se deduce siempre de las penalizaciones vigentes.</p>
 *
 * @param penalizacionesVigentes cancelaciones tardias dentro de la ventana movil
 * @param bloqueado              si tiene prohibido agendar nuevas citas ahora mismo
 * @param puedeAgendarDesde      instante en que dejara de estarlo; {@code null} si no lo esta
 */
public record EstadoDeBloqueo(
        int penalizacionesVigentes,
        boolean bloqueado,
        LocalDateTime puedeAgendarDesde) {

    public static EstadoDeBloqueo sinBloqueo(int penalizacionesVigentes) {
        return new EstadoDeBloqueo(penalizacionesVigentes, false, null);
    }

    public static EstadoDeBloqueo conBloqueo(
            int penalizacionesVigentes, LocalDateTime puedeAgendarDesde) {
        return new EstadoDeBloqueo(penalizacionesVigentes, true, puedeAgendarDesde);
    }
}
