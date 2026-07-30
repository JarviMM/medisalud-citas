package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Consecuencias de la RN-05 para el paciente tras una operacion.
 *
 * <p>Lo comparten cancelar y reprogramar, que son las dos operaciones capaces de generar
 * una penalizacion. Se extrajo a un tipo propio en cuanto aparecio la segunda: repetir
 * estos cinco campos en dos respuestas obligaria a cambiarlas las dos cada vez que la
 * politica de penalizaciones exponga algo nuevo.</p>
 *
 * @param registrada        si la operacion genero una penalizacion
 * @param motivo            antelacion concreta con la que se cancelo; nulo si no penalizo
 * @param totalVigentes     penalizaciones dentro de la ventana de 30 dias, ya contando esta
 * @param pacienteBloqueado si a partir de ahora no puede agendar nuevas citas
 * @param puedeAgendarDesde cuando se le levanta el bloqueo; nulo si no esta bloqueado
 */
public record ResumenDePenalizacion(
        boolean registrada,
        String motivo,
        int totalVigentes,
        boolean pacienteBloqueado,
        LocalDateTime puedeAgendarDesde) {

    public static ResumenDePenalizacion de(
            Optional<PenalizacionPaciente> penalizacion, EstadoDeBloqueo bloqueo) {
        return new ResumenDePenalizacion(
                penalizacion.isPresent(),
                penalizacion.map(PenalizacionPaciente::getMotivo).orElse(null),
                bloqueo.penalizacionesVigentes(),
                bloqueo.bloqueado(),
                bloqueo.puedeAgendarDesde());
    }
}
