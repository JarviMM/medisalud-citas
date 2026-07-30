package com.medisalud.agenda.domain;

/**
 * Ciclo de vida de una cita.
 *
 * <p>Transiciones validas: {@code PROGRAMADA -> CANCELADA} y
 * {@code PROGRAMADA -> ATENDIDA}. Los estados finales no admiten cambios.</p>
 *
 * <p>Se persiste como {@code STRING} y no como ordinal: insertar un valor nuevo en medio
 * del enum reescribiria el significado de los datos ya guardados.</p>
 */
public enum EstadoCita {

    /** Cita vigente. Es el unico estado que ocupa una franja horaria (RN-02, RN-04). */
    PROGRAMADA,

    /** Cita anulada. Libera la franja y puede generar penalizacion (RN-05). */
    CANCELADA,

    /** Cita ya realizada. */
    ATENDIDA
}
