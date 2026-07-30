package com.medisalud.agenda.domain;

import java.time.LocalDate;

/**
 * Punto de extension para el calendario de festivos (el {@code HolidayProvider} del
 * enunciado).
 *
 * <p>El enunciado no aporta lista de festivos, asi que la implementacion registrada por
 * defecto no considera festivo ningun dia. Se modela como interfaz y no como una lista
 * fija por dos razones: los festivos colombianos siguen la Ley Emiliani y se desplazan al
 * lunes siguiente, de modo que no son constantes calculables de forma trivial; y en
 * produccion esto se resolveria consultando un calendario externo o una tabla
 * administrable, no recompilando la aplicacion.</p>
 *
 * <p>Para sustituirla basta con declarar otro bean de este tipo: el stub por defecto se
 * registra con {@code @ConditionalOnMissingBean} y se retira solo.</p>
 */
@FunctionalInterface
public interface ProveedorDeFestivos {

    /**
     * @param fecha dia a comprobar
     * @return {@code true} si ese dia no hay atencion por ser festivo
     */
    boolean esFestivo(LocalDate fecha);
}
