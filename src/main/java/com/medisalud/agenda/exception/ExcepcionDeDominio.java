package com.medisalud.agenda.exception;

import lombok.Getter;

/**
 * Raiz de las excepciones que representan un fallo previsto de las reglas del dominio.
 *
 * <p>Deliberadamente <b>no</b> conoce HTTP: no lleva {@code HttpStatus} ni
 * {@code @ResponseStatus}. Una excepcion de dominio describe que regla se incumplio; a que
 * codigo de respuesta se traduce eso es una decision de la capa web y vive en
 * {@link ManejadorGlobalDeErrores}. Esa separacion es lo que permitiria exponer el mismo
 * servicio por otro transporte sin arrastrar semantica HTTP hasta el nucleo.</p>
 *
 * <p>Extiende {@link RuntimeException} y no {@code Exception} para no obligar a cada
 * firma intermedia a declarar {@code throws}: son fallos que el llamante no puede
 * recuperar, solo reportar.</p>
 */
@Getter
public abstract class ExcepcionDeDominio extends RuntimeException {

    /** Codigo estable que se devolvera al cliente en el cuerpo del error. */
    private final transient CodigoError codigo;

    protected ExcepcionDeDominio(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }
}
