package com.medisalud.agenda.exception;

/**
 * La peticion incumple una regla de negocio que no depende del estado del sistema. Se
 * traduce a 400.
 *
 * <p>Complementa a {@link ConflictoDeNegocioException}: aqui caen los incumplimientos que
 * volverian a fallar aunque el sistema cambiara por completo (una cita un domingo, una
 * hora desalineada de la franja, una fecha ya pasada). No es un 409 porque no hay
 * conflicto con nada: la peticion sencillamente no describe una cita posible.</p>
 *
 * <p>No se resuelve con Bean Validation en el DTO porque estas reglas necesitan
 * colaboradores del dominio: el calendario laboral, el proveedor de festivos y el reloj
 * inyectado. Meter esas dependencias en una anotacion sobre el DTO trasladaria reglas de
 * negocio a la capa de contrato, que es justo lo que se quiere evitar.</p>
 */
public class SolicitudInvalidaException extends ExcepcionDeDominio {

    public SolicitudInvalidaException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
