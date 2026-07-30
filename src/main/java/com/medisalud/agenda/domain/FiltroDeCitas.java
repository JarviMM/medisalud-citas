package com.medisalud.agenda.domain;

import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import java.time.LocalDate;

/**
 * Criterios de busqueda del listado de citas (RF-06). Todos son opcionales y se combinan
 * con AND; un filtro entero a nulos devuelve todas las citas.
 *
 * <p><b>Por que un objeto y no cinco parametros sueltos:</b> el controller, el servicio y el
 * constructor de la {@code Specification} tendrian que arrastrar la misma lista de cinco
 * argumentos, y anadir un sexto filtro obligaria a cambiar las tres firmas. Ademas permite
 * que la unica combinacion invalida se rechace aqui, de modo que un filtro incoherente no
 * llega a existir.</p>
 *
 * <p><b>Semantica del rango:</b> las fechas son dias completos, no instantes. El rango
 * cubre desde las 00:00 de {@code fechaInicio} hasta las 23:59:59 de {@code fechaFin},
 * ambos inclusive. Se eligio {@code LocalDate} y no {@code LocalDateTime} por coherencia
 * con la consulta de disponibilidad y porque nadie filtra una agenda por minutos.</p>
 *
 * @param medicoId    solo citas de este medico
 * @param pacienteId  solo citas de este paciente
 * @param estado      solo citas en este estado
 * @param fechaInicio primer dia incluido
 * @param fechaFin    ultimo dia incluido
 */
public record FiltroDeCitas(
        Long medicoId,
        Long pacienteId,
        EstadoCita estado,
        LocalDate fechaInicio,
        LocalDate fechaFin) {

    public FiltroDeCitas {
        if (fechaInicio != null && fechaFin != null && fechaFin.isBefore(fechaInicio)) {
            throw new SolicitudInvalidaException(CodigoError.RANGO_DE_FECHAS_INVALIDO,
                    "La fecha de fin no puede ser anterior a la de inicio.");
        }
    }
}
