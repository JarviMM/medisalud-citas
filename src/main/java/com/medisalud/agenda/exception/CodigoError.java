package com.medisalud.agenda.exception;

/**
 * Taxonomia de errores que devuelve la API.
 *
 * <p>El codigo viaja en el cuerpo de la respuesta junto al mensaje. Existe porque el
 * estado HTTP por si solo es ambiguo para un cliente: un 409 puede significar documento
 * duplicado, franja ocupada o paciente bloqueado, y cada caso exige una reaccion distinta
 * en la interfaz. El mensaje esta pensado para leerse; el codigo, para programarse contra
 * el, de modo que reformular un texto nunca rompa a un cliente.</p>
 *
 * <h2>Criterio para elegir entre 400 y 409</h2>
 *
 * <p>Es una decision de diseno explicita, porque varias reglas de negocio podrian
 * defenderse en ambos codigos:</p>
 * <ul>
 *   <li><b>400</b> cuando la peticion es invalida en si misma y volveria a fallar siempre,
 *       haga lo que haga el sistema: un domingo nunca sera laborable y las 08:15 nunca
 *       seran el inicio de una franja. El cliente tiene que cambiar la peticion.</li>
 *   <li><b>409</b> cuando la peticion es correcta pero choca con el estado actual: la
 *       franja esta ocupada, el paciente ya tiene otra cita. Si ese estado cambia, la
 *       misma peticion tendria exito.</li>
 * </ul>
 */
public enum CodigoError {

    // ----------------------------------------------------------------- 400

    /** El cuerpo de la peticion no supero Bean Validation. Incluye detalle por campo. */
    VALIDACION_FALLIDA,

    /** La peticion es incorrecta por otra razon (JSON ilegible, parametro mal tipado). */
    PETICION_INVALIDA,

    /** RN-01: la hora no coincide con el inicio de una franja de 30 minutos. */
    FRANJA_NO_VALIDA,

    /** RN-01: domingo, festivo, o fuera del horario de atencion de ese dia. */
    FUERA_DE_HORARIO_LABORAL,

    /** La cita se solicito para un instante que ya paso. */
    FECHA_EN_EL_PASADO,

    /** RN-03: el paciente tiene registrada una fecha de nacimiento futura. */
    FECHA_NACIMIENTO_INVALIDA,

    // ----------------------------------------------------------------- 404

    /** El recurso solicitado no existe. */
    RECURSO_NO_ENCONTRADO,

    // ----------------------------------------------------------------- 409

    /** Ya hay un paciente registrado con ese documento de identidad. */
    DOCUMENTO_DUPLICADO,

    /** RN-02: el medico ya tiene una cita programada en esa franja. */
    MEDICO_NO_DISPONIBLE,

    /** RN-04: el paciente ya tiene una cita programada en esa franja, con cualquier medico. */
    PACIENTE_NO_DISPONIBLE,

    /** La franja fue ocupada por otra peticion concurrente entre la validacion y el INSERT. */
    FRANJA_OCUPADA,

    /** La operacion viola una restriccion de integridad del modelo. */
    CONFLICTO_DE_INTEGRIDAD,

    // ----------------------------------------------------------------- 500

    /** Fallo no controlado. Nunca expone detalles internos al cliente. */
    ERROR_INTERNO
}
