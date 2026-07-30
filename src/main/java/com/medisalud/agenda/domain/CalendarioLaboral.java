package com.medisalud.agenda.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Unica fuente de verdad sobre cuando se puede atender (RN-01).
 *
 * <p>Concentra aqui el horario laboral evita que la regla se disperse: el validador de
 * reservas y el calculo de disponibilidad (RF-04) consultan este mismo objeto, de modo que
 * es imposible que una franja se considere valida al reservar y no aparezca como
 * disponible, o al reves.</p>
 *
 * <p>Es un componente de dominio sin estado ni persistencia. Vive junto a las entidades
 * porque describe una regla del negocio, no un detalle de infraestructura.</p>
 */
@Component
@RequiredArgsConstructor
public class CalendarioLaboral {

    /** Duracion de cada franja segun el enunciado. */
    public static final Duration DURACION_FRANJA = Duration.ofMinutes(30);

    /**
     * Jornada de cada dia de la semana. El domingo esta deliberadamente ausente: no hay
     * atencion, y modelarlo como ausencia en vez de como una jornada vacia hace imposible
     * construir por error un domingo con horario.
     */
    private static final Map<DayOfWeek, JornadaLaboral> JORNADAS = Map.of(
            DayOfWeek.MONDAY, jornadaDeSemana(),
            DayOfWeek.TUESDAY, jornadaDeSemana(),
            DayOfWeek.WEDNESDAY, jornadaDeSemana(),
            DayOfWeek.THURSDAY, jornadaDeSemana(),
            DayOfWeek.FRIDAY, jornadaDeSemana(),
            DayOfWeek.SATURDAY, new JornadaLaboral(LocalTime.of(8, 0), LocalTime.of(13, 0)));

    private final ProveedorDeFestivos proveedorDeFestivos;

    private static JornadaLaboral jornadaDeSemana() {
        return new JornadaLaboral(LocalTime.of(8, 0), LocalTime.of(18, 0));
    }

    /**
     * Jornada aplicable a una fecha concreta.
     *
     * @return la jornada, o vacio si ese dia no hay atencion (domingo o festivo)
     */
    public Optional<JornadaLaboral> jornadaDe(LocalDate fecha) {
        if (proveedorDeFestivos.esFestivo(fecha)) {
            return Optional.empty();
        }
        return Optional.ofNullable(JORNADAS.get(fecha.getDayOfWeek()));
    }

    /** Horas de inicio validas de un dia; vacia si ese dia no hay atencion. */
    public List<LocalTime> franjasDe(LocalDate fecha) {
        return jornadaDe(fecha)
                .map(jornada -> jornada.franjas(DURACION_FRANJA))
                .orElseGet(List::of);
    }

    /** {@code true} si ese dia se atiende (ni domingo ni festivo). */
    public boolean esDiaHabil(LocalDate fecha) {
        return jornadaDe(fecha).isPresent();
    }

    /**
     * Comprueba que la hora coincide exactamente con el inicio de una franja.
     *
     * <p>Se valida por separado de {@link #estaDentroDeLaJornada(LocalDateTime)} para poder
     * distinguir dos errores que el cliente corrige de forma distinta: una cita a las 08:15
     * esta dentro del horario pero desalineada, mientras que una a las 20:00 esta alineada
     * pero fuera de la jornada.</p>
     */
    public boolean esInicioDeFranja(LocalDateTime fechaHora) {
        LocalTime hora = fechaHora.toLocalTime();
        return hora.getSecond() == 0
                && hora.getNano() == 0
                && hora.getMinute() % DURACION_FRANJA.toMinutes() == 0;
    }

    /**
     * Comprueba que la franja existe realmente en el calendario de ese dia.
     *
     * <p>Se resuelve preguntando por la lista de franjas del dia en lugar de comparando
     * contra las horas de apertura y cierre: asi esta comprobacion y el calculo de
     * disponibilidad no pueden divergir nunca, porque son literalmente el mismo codigo.</p>
     */
    public boolean estaDentroDeLaJornada(LocalDateTime fechaHora) {
        return franjasDe(fechaHora.toLocalDate()).contains(fechaHora.toLocalTime());
    }
}
