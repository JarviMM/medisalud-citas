package com.medisalud.agenda.service;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.PenalizacionPaciente;
import com.medisalud.agenda.repository.PenalizacionPacienteRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toda la RN-05 en un solo sitio: cuando se penaliza una cancelacion y cuando eso bloquea
 * al paciente.
 *
 * <p>Vive fuera de {@code CitaService} porque son dos reglas con vida propia que se
 * consultan desde dos sitios distintos: la cancelacion registra penalizaciones y el
 * agendamiento pregunta por el bloqueo. Si estuviera repartida, cambiar el umbral de 2
 * horas o la ventana de 30 dias obligaria a tocar varias clases y a confiar en que no se
 * olvida ninguna.</p>
 */
@Component
@RequiredArgsConstructor
public class PoliticaDePenalizaciones {

    /**
     * Antelacion a partir de la cual cancelar no penaliza.
     *
     * <p><b>Supuesto sobre el limite:</b> el enunciado dice "menos de 2 horas", de modo que
     * cancelar exactamente con 2 horas de antelacion <b>no</b> penaliza. El limite se
     * interpreta a favor del paciente.</p>
     */
    public static final Duration ANTELACION_SIN_PENALIZACION = Duration.ofHours(2);

    /** Penalizaciones vigentes a partir de las cuales el paciente no puede agendar. */
    public static final int PENALIZACIONES_PARA_BLOQUEO = 3;

    /** Ventana movil dentro de la que cuenta una penalizacion. */
    public static final Period VENTANA_VIGENCIA = Period.ofDays(30);

    private static final DateTimeFormatter FORMATO_LEGIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final PenalizacionPacienteRepository penalizacionRepository;
    private final Clock clock;

    /**
     * Registra una penalizacion si la cancelacion llega tarde, y nada si llega a tiempo.
     *
     * <p>La antelacion se mide entre el momento de la cancelacion y la hora de la cita.
     * Cuando la cita ya paso la duracion es negativa, lo que la deja automaticamente por
     * debajo del umbral: cancelar despues de la hora es el peor caso posible y penaliza,
     * igual que cancelar cinco minutos antes.</p>
     *
     * @param cita               cita que se esta cancelando
     * @param momentoCancelacion instante exacto de la cancelacion
     * @return la penalizacion creada, o vacio si la cancelacion fue con suficiente antelacion
     */
    @Transactional
    public Optional<PenalizacionPaciente> registrarSiLaCancelacionEsTardia(
            Cita cita, LocalDateTime momentoCancelacion) {

        Duration antelacion = Duration.between(momentoCancelacion, cita.getFechaHora());
        if (antelacion.compareTo(ANTELACION_SIN_PENALIZACION) >= 0) {
            return Optional.empty();
        }

        PenalizacionPaciente penalizacion = PenalizacionPaciente.builder()
                .paciente(cita.getPaciente())
                .cita(cita)
                .fechaRegistro(momentoCancelacion)
                .motivo(describirAntelacion(antelacion))
                .build();

        return Optional.of(penalizacionRepository.save(penalizacion));
    }

    /**
     * Calcula si el paciente puede agendar ahora mismo y, si no, desde cuando podra.
     *
     * <p><b>Como se obtiene la fecha de desbloqueo:</b> el paciente deja de estar bloqueado
     * en cuanto le quedan menos de tres penalizaciones vigentes. Con las penalizaciones
     * ordenadas de mas antigua a mas reciente, eso ocurre justo cuando expira la
     * tercera empezando por el final, porque a partir de ese momento solo quedan dos dentro
     * de la ventana. Con exactamente tres penalizaciones esa es la mas antigua, que es el
     * caso que describe el enunciado; con cinco, es la tercera, y el enunciado no lo cubre.
     * Calcularlo por posicion en vez de tomar siempre la mas antigua evita prometer una
     * fecha de desbloqueo que llegaria con el paciente todavia bloqueado.</p>
     */
    public EstadoDeBloqueo evaluarBloqueo(Long pacienteId) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<LocalDateTime> vigentes = penalizacionRepository.buscarFechasVigentes(
                pacienteId, ahora.minus(VENTANA_VIGENCIA));

        if (vigentes.size() < PENALIZACIONES_PARA_BLOQUEO) {
            return EstadoDeBloqueo.sinBloqueo(vigentes.size());
        }

        LocalDateTime determinante = vigentes.get(vigentes.size() - PENALIZACIONES_PARA_BLOQUEO);
        return EstadoDeBloqueo.conBloqueo(
                vigentes.size(), determinante.plus(VENTANA_VIGENCIA));
    }

    /** Mensaje para el 409 del agendamiento: dice cuantas lleva y cuando se le levanta. */
    public String describirBloqueo(EstadoDeBloqueo estado) {
        return ("El paciente acumula %d cancelaciones tardías en los últimos %d días "
                + "y no puede agendar nuevas citas hasta el %s.")
                .formatted(
                        estado.penalizacionesVigentes(),
                        VENTANA_VIGENCIA.getDays(),
                        estado.puedeAgendarDesde().format(FORMATO_LEGIBLE));
    }

    /** Texto auditable que queda guardado con la penalizacion. */
    private String describirAntelacion(Duration antelacion) {
        if (antelacion.isNegative()) {
            return "Cancelación posterior a la hora de la cita.";
        }
        long horas = antelacion.toHours();
        long minutos = antelacion.toMinutesPart();
        return horas > 0
                ? "Cancelación con %d h %d min de antelación.".formatted(horas, minutos)
                : "Cancelación con %d min de antelación.".formatted(minutos);
    }
}
