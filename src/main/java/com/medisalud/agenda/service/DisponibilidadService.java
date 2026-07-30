package com.medisalud.agenda.service;

import com.medisalud.agenda.domain.CalendarioLaboral;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.dto.DisponibilidadResponse;
import com.medisalud.agenda.dto.DisponibilidadResponse.DiaDisponible;
import com.medisalud.agenda.dto.MedicoResumen;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import com.medisalud.agenda.repository.CitaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculo de franjas libres de un medico (RF-04).
 *
 * <p>El servicio es corto porque la parte dificil ya estaba resuelta: que franjas existen
 * cada dia lo decide {@link CalendarioLaboral}, el mismo objeto que valida las reservas. De
 * ahi que aqui solo quede restar lo que no se puede ofrecer.</p>
 *
 * <p><b>Se descartan tres cosas, y las tres importan:</b></p>
 * <ol>
 *   <li>Los dias sin atencion, que el calendario ya devuelve sin franjas.</li>
 *   <li>Las franjas con una cita PROGRAMADA de ese medico. Las canceladas no cuentan,
 *       porque liberan el horario (RN-02).</li>
 *   <li>Las franjas que ya pasaron. Sin este filtro, consultar la disponibilidad de hoy a
 *       media tarde ofreceria horas de la manana que el propio agendamiento rechazaria
 *       despues con un 400. El endpoint no debe proponer nada que no se pueda reservar.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisponibilidadService {

    /**
     * Tope de dias consultables en una sola peticion.
     *
     * <p>Sin limite, {@code ?fechaInicio=2026-01-01&fechaFin=2126-01-01} generaria cientos
     * de miles de franjas en memoria por una peticion sin autenticar. El tope acota ese
     * coste sin estorbar al uso real, que es pintar una agenda de dias o semanas.</p>
     */
    private static final int MAXIMO_DIAS_CONSULTABLES = 90;

    private final MedicoService medicoService;
    private final CitaRepository citaRepository;
    private final CalendarioLaboral calendario;
    private final Clock clock;

    /**
     * @param medicoId    medico cuya agenda se consulta
     * @param fechaInicio primer dia del rango, inclusive
     * @param fechaFin    ultimo dia del rango, inclusive
     * @throws SolicitudInvalidaException si el rango esta invertido o excede el maximo
     */
    public DisponibilidadResponse calcular(Long medicoId, LocalDate fechaInicio, LocalDate fechaFin) {
        // El rango se valida antes de tocar la base de datos: es mas barato y no tiene
        // sentido resolver el medico para una consulta que de todos modos se va a rechazar.
        validarRango(fechaInicio, fechaFin);
        Medico medico = medicoService.buscarEntidad(medicoId);

        Set<LocalDateTime> ocupadas = new HashSet<>(citaRepository.buscarHorasOcupadas(
                medicoId,
                EstadoCita.PROGRAMADA,
                fechaInicio.atStartOfDay(),
                fechaFin.plusDays(1).atStartOfDay()));

        LocalDateTime ahora = LocalDateTime.now(clock);

        List<DiaDisponible> dias = fechaInicio.datesUntil(fechaFin.plusDays(1))
                .filter(calendario::esDiaHabil)
                .map(fecha -> new DiaDisponible(fecha, franjasLibresDe(fecha, ocupadas, ahora)))
                .toList();

        int total = dias.stream().mapToInt(dia -> dia.franjas().size()).sum();

        return new DisponibilidadResponse(
                MedicoResumen.desde(medico), fechaInicio, fechaFin, total, dias);
    }

    private List<LocalTime> franjasLibresDe(
            LocalDate fecha, Set<LocalDateTime> ocupadas, LocalDateTime ahora) {
        return calendario.franjasDe(fecha).stream()
                .filter(hora -> fecha.atTime(hora).isAfter(ahora))
                .filter(hora -> !ocupadas.contains(fecha.atTime(hora)))
                .toList();
    }

    private void validarRango(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaFin.isBefore(fechaInicio)) {
            throw new SolicitudInvalidaException(CodigoError.RANGO_DE_FECHAS_INVALIDO,
                    "La fecha de fin no puede ser anterior a la de inicio.");
        }
        long dias = ChronoUnit.DAYS.between(fechaInicio, fechaFin) + 1;
        if (dias > MAXIMO_DIAS_CONSULTABLES) {
            throw new SolicitudInvalidaException(CodigoError.RANGO_DE_FECHAS_INVALIDO,
                    "El rango solicitado abarca %d días y el máximo consultable es %d."
                            .formatted(dias, MAXIMO_DIAS_CONSULTABLES));
        }
    }
}
