package com.medisalud.agenda.exception;

/**
 * La peticion es sintacticamente valida pero choca con el estado actual del sistema o con
 * una regla de negocio. Se traduce a 409.
 *
 * <p>Es la diferencia con un 400: el cliente no envio nada mal formado, simplemente lo que
 * pide no es posible ahora mismo (documento ya registrado, franja ocupada, paciente
 * bloqueado). Reintentar la misma peticion sin cambiar el estado del sistema volveria a
 * fallar igual.</p>
 */
public class ConflictoDeNegocioException extends ExcepcionDeDominio {

    public ConflictoDeNegocioException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
