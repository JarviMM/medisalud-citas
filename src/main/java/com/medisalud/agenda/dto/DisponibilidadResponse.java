package com.medisalud.agenda.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Franjas libres de un medico dentro de un rango de fechas (RF-04).
 *
 * <p><b>Por que agrupado por dia y no una lista plana de instantes:</b> una lista plana
 * repetiria la fecha en cada una de las veinte franjas de cada dia y obligaria al cliente a
 * reagrupar para pintar un calendario, que es el uso real de este endpoint. Agrupar reduce
 * el tamano de la respuesta y la deja lista para consumir.</p>
 *
 * <p><b>Que dias aparecen:</b> los dias sin atencion (domingos y festivos) se omiten por
 * completo, tal y como pide el enunciado. Un dia laborable sin franjas libres si aparece,
 * con la lista vacia, porque "esta todo reservado" es informacion distinta de "ese dia no
 * atendemos" y el cliente necesita poder distinguirlas.</p>
 *
 * <p>Para reservar, la {@code fechaHora} del POST se compone como
 * {@code fecha + "T" + franja}, por ejemplo {@code 2026-08-03T08:30:00}.</p>
 *
 * @param medico                  medico consultado
 * @param fechaInicio             primer dia del rango, inclusive
 * @param fechaFin                ultimo dia del rango, inclusive
 * @param totalFranjasDisponibles suma de franjas libres de todos los dias
 * @param dias                    dias con atencion dentro del rango, en orden ascendente
 */
public record DisponibilidadResponse(
        MedicoResumen medico,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        int totalFranjasDisponibles,
        List<DiaDisponible> dias) {

    /**
     * Franjas libres de un dia concreto.
     *
     * @param fecha   dia al que pertenecen las franjas
     * @param franjas horas de inicio libres, en orden ascendente
     */
    public record DiaDisponible(LocalDate fecha, List<LocalTime> franjas) {
    }
}
