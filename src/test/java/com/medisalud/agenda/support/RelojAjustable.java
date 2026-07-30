package com.medisalud.agenda.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Reloj de pruebas que se puede situar en un instante concreto y hacer avanzar.
 *
 * <p>{@code Clock.fixed} basta para comprobar una regla en un instante, pero la RN-05 es
 * una regla que se despliega en el tiempo: hay que cancelar tarde, dejar pasar dias,
 * acumular penalizaciones y esperar a que la ventana expire. Eso exige un reloj que avance
 * bajo control del test, no uno congelado ni el del sistema.</p>
 *
 * <p>Es la pieza que hace que inyectar {@link Clock} en el dominio valga la pena: un ciclo
 * de bloqueo y desbloqueo de 30 dias se verifica en milisegundos y sin datos falsificados.</p>
 */
public class RelojAjustable extends Clock {

    private final ZoneId zona;
    private Instant instante;

    public RelojAjustable(LocalDateTime momentoInicial, ZoneId zona) {
        this.zona = zona;
        this.instante = momentoInicial.atZone(zona).toInstant();
    }

    /** Situa el reloj en un momento concreto. */
    public void situarEn(LocalDateTime momento) {
        this.instante = momento.atZone(zona).toInstant();
    }

    /** Hace avanzar el reloj. */
    public void avanzar(Duration duracion) {
        this.instante = this.instante.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        return new RelojAjustable(LocalDateTime.ofInstant(instante, otraZona), otraZona);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
