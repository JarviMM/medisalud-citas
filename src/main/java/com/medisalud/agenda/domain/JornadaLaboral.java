package com.medisalud.agenda.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tramo de atencion de un dia: desde que hora abre la consulta hasta que hora cierra.
 *
 * @param apertura hora de inicio de la atencion, inclusive
 * @param cierre   hora de fin de la atencion, exclusive como hora de inicio de cita
 */
public record JornadaLaboral(LocalTime apertura, LocalTime cierre) {

    public JornadaLaboral {
        if (apertura == null || cierre == null || !apertura.isBefore(cierre)) {
            throw new IllegalArgumentException(
                    "La hora de apertura debe ser anterior a la de cierre: %s - %s"
                            .formatted(apertura, cierre));
        }
    }

    /**
     * Horas de inicio de todas las franjas completas que caben en la jornada.
     *
     * <p><b>Supuesto sobre el limite de la jornada:</b> el enunciado dice "08:00-18:00"
     * sin aclarar si las 18:00 son la hora de inicio de la ultima cita o la hora a la que
     * la consulta cierra. Se adopta lo segundo: una cita solo es valida si <i>termina</i>
     * dentro de la jornada, asi que la ultima franja de un dia de semana empieza a las
     * 17:30 y la del sabado a las 12:30. Lo contrario dejaria a un paciente atendido media
     * hora despues del cierre.</p>
     *
     * @param duracion duracion de cada franja
     * @return horas de inicio en orden ascendente; lista vacia si no cabe ninguna franja
     */
    public List<LocalTime> franjas(Duration duracion) {
        List<LocalTime> franjas = new ArrayList<>();
        for (LocalTime inicio = apertura;
                !inicio.plus(duracion).isAfter(cierre);
                inicio = inicio.plus(duracion)) {
            franjas.add(inicio);
        }
        return List.copyOf(franjas);
    }
}
