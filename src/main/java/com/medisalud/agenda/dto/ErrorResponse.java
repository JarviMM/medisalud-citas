package com.medisalud.agenda.dto;

import com.medisalud.agenda.exception.CodigoError;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cuerpo unico de todas las respuestas de error de la API.
 *
 * <p>Un solo formato para 400, 404, 409 y 500 permite que el cliente escriba un unico
 * manejador de errores. {@code detalles} solo aparece en los fallos de validacion; en el
 * resto viaja como {@code null} y Jackson lo omite del JSON gracias a
 * {@code default-property-inclusion: non_null}.</p>
 *
 * <p>El {@code timestamp} no es decorativo: en un 500 el mensaje al cliente es
 * deliberadamente generico, y esa marca de tiempo es lo que permite localizar en el log
 * del servidor la traza concreta que lo origino.</p>
 *
 * @param codigo    identificador estable del tipo de error, apto para programar contra el
 * @param mensaje   descripcion legible, nunca con detalles internos del sistema
 * @param timestamp instante en que se genero la respuesta
 * @param ruta      recurso sobre el que se produjo el error
 * @param detalles  errores por campo; solo presente en fallos de validacion
 */
public record ErrorResponse(
        CodigoError codigo,
        String mensaje,
        OffsetDateTime timestamp,
        String ruta,
        List<DetalleCampo> detalles) {

    /**
     * Error concreto sobre un campo del cuerpo de la peticion.
     *
     * @param campo   nombre del campo tal y como se envio
     * @param mensaje motivo del rechazo
     */
    public record DetalleCampo(String campo, String mensaje) {
    }
}
