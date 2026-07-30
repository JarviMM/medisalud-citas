package com.medisalud.agenda.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

/**
 * Datos de entrada para reservar una cita.
 *
 * <p>Aqui solo se valida la <i>forma</i> de la peticion: que los identificadores vengan y
 * sean positivos, y que haya fecha y hora. Que esa fecha caiga en horario laboral, en una
 * franja de 30 minutos y sin solapar con otras citas son reglas de negocio (RN-01, RN-02,
 * RN-04) y se validan en el dominio, donde estan el calendario, el reloj y el repositorio
 * que hacen falta para decidirlo.</p>
 *
 * <p>{@code fechaHora} se recibe en ISO-8601 sin zona, por ejemplo
 * {@code "2026-08-03T09:00:00"}. El sistema opera sobre la hora local de la clinica, asi
 * que un {@code LocalDateTime} refleja el dominio con mas fidelidad que un instante con
 * huso: la consulta abre a las 08:00 locales, no a un instante UTC concreto.</p>
 */
public record CrearCitaRequest(

        @NotNull(message = "El identificador del médico es obligatorio.")
        @Positive(message = "El identificador del médico debe ser un número positivo.")
        Long medicoId,

        @NotNull(message = "El identificador del paciente es obligatorio.")
        @Positive(message = "El identificador del paciente debe ser un número positivo.")
        Long pacienteId,

        @NotNull(message = "La fecha y hora de la cita son obligatorias.")
        LocalDateTime fechaHora) {
}
