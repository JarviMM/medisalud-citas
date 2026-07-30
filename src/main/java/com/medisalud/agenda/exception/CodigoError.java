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
 * <p>Se ira ampliando con los codigos propios de las reglas de negocio de los pasos
 * siguientes (franja ocupada, paciente bloqueado, horario no laboral).</p>
 */
public enum CodigoError {

    /** 400: el cuerpo de la peticion no supero Bean Validation. Incluye detalle por campo. */
    VALIDACION_FALLIDA,

    /** 400: la peticion es incorrecta por otra razon (JSON ilegible, parametro mal tipado). */
    PETICION_INVALIDA,

    /** 404: el recurso solicitado no existe. */
    RECURSO_NO_ENCONTRADO,

    /** 409: ya hay un paciente registrado con ese documento de identidad. */
    DOCUMENTO_DUPLICADO,

    /** 409: la operacion viola una restriccion de integridad del modelo. */
    CONFLICTO_DE_INTEGRIDAD,

    /** 500: fallo no controlado. Nunca expone detalles internos al cliente. */
    ERROR_INTERNO
}
