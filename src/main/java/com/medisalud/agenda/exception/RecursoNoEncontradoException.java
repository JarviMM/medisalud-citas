package com.medisalud.agenda.exception;

/**
 * El recurso referenciado por la peticion no existe. Se traduce a 404.
 */
public class RecursoNoEncontradoException extends ExcepcionDeDominio {

    /**
     * @param recurso       nombre del recurso con su articulo, p. ej. {@code "el médico"}
     * @param identificador identificador buscado, ya convertido al tipo esperado
     */
    public RecursoNoEncontradoException(String recurso, Object identificador) {
        super(CodigoError.RECURSO_NO_ENCONTRADO,
                "No se encontró %s con id %s.".formatted(recurso, identificador));
    }
}
