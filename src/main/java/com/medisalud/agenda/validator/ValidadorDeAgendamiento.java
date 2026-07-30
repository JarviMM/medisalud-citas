package com.medisalud.agenda.validator;

import com.medisalud.agenda.domain.CalendarioLaboral;
import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.EstadoDeBloqueo;
import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.SolicitudInvalidaException;
import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.service.PoliticaDePenalizaciones;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Aplica las reglas de negocio que debe cumplir una cita para poder agendarse.
 *
 * <p>Vive fuera de {@code CitaService} porque son varias reglas independientes con sus
 * propias dependencias; dejarlas dentro del servicio lo convertiria en una clase que
 * valida, orquesta y persiste a la vez. Aqui se puede probar cada regla contra este objeto
 * aislado, sin montar el caso de uso completo.</p>
 *
 * <p><b>Por que no una cadena de estrategias:</b> se valoro modelar cada regla como un bean
 * {@code ReglaDeAgendamiento} y recorrerlas con un {@code List} inyectado. Se descarto: con
 * un conjunto fijo y conocido de reglas, esa indireccion esconde el orden de evaluacion
 * (que si importa: no tiene sentido consultar la agenda del medico para una hora que ni
 * siquiera es una franja valida) y obliga a abrir un fichero por regla para entender que se
 * valida. Un metodo publico que enumera las reglas en orden se lee de un vistazo. La
 * abstraccion se justificaria si las reglas fueran configurables por sede o por
 * especialidad, que no es el caso.</p>
 *
 * <p>El orden va de lo mas barato a lo mas caro, y dentro de eso, de lo mas general a lo
 * mas concreto: primero lo que se decide sin tocar la base de datos, despues el bloqueo del
 * paciente (que invalida cualquier franja) y por ultimo las dos consultas de
 * solapamiento.</p>
 */
@Component
@RequiredArgsConstructor
public class ValidadorDeAgendamiento {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarioLaboral calendario;
    private final CitaRepository citaRepository;
    private final PoliticaDePenalizaciones politicaDePenalizaciones;
    private final Clock clock;

    /**
     * Valida una cita nueva y no devuelve nada: o pasa, o lanza la excepcion que describe la
     * primera regla incumplida.
     *
     * @param medico     medico ya resuelto
     * @param paciente   paciente ya resuelto
     * @param fechaHora  inicio de franja solicitado
     * @throws SolicitudInvalidaException   400: la cita no es posible en ningun escenario
     * @throws ConflictoDeNegocioException  409: choca con el estado actual de las agendas
     */
    public void validarReserva(Medico medico, Paciente paciente, LocalDateTime fechaHora) {
        validarFranjaSolicitada(paciente, fechaHora);
        validarQueNoEsteBloqueado(paciente);
        validarAgendasLibres(medico, paciente, fechaHora);
    }

    /**
     * Valida el nuevo horario de una reprogramacion (RN-06).
     *
     * <p><b>Deliberadamente no comprueba el bloqueo de la RN-05</b>, y es la unica
     * diferencia con {@link #validarReserva}. El enunciado enumera para la reprogramacion la
     * RN-02 y la RN-04, no la RN-05, y la lectura literal coincide con la sensata: un
     * paciente bloqueado que no pudiera mover una cita ya existente solo tendria dos
     * salidas, no presentarse o cancelarla, y cancelar le sumaria <i>otra</i> penalizacion.
     * Bloquear la reprogramacion empujaria justo a la conducta que la regla quiere
     * evitar.</p>
     *
     * <p>Reprogramar tarde si registra penalizacion: lo que no hace es impedir la operacion
     * que la genera. La distincion es entre castigar el aviso tardio, que se sigue haciendo,
     * y quitarle al paciente la unica via ordenada de resolverlo.</p>
     */
    public void validarReprogramacion(Medico medico, Paciente paciente, LocalDateTime fechaHora) {
        validarFranjaSolicitada(paciente, fechaHora);
        validarAgendasLibres(medico, paciente, fechaHora);
    }

    /** Reglas que dependen solo de la franja y del paciente, sin tocar la base de datos. */
    private void validarFranjaSolicitada(Paciente paciente, LocalDateTime fechaHora) {
        validarQueEstaEnElFuturo(fechaHora);
        validarAlineacionDeFranja(fechaHora);
        validarHorarioLaboral(fechaHora);
        validarFechaDeNacimiento(paciente);
    }

    /** RN-02 y RN-04: las dos consultas de solapamiento. */
    private void validarAgendasLibres(
            Medico medico, Paciente paciente, LocalDateTime fechaHora) {
        validarAgendaDelMedico(medico, fechaHora);
        validarAgendaDelPaciente(paciente, fechaHora);
    }

    /**
     * <b>Supuesto:</b> el enunciado no lo pide explicitamente, pero agendar en el pasado no
     * describe ninguna operacion real y ademas dejaria sin sentido la RN-05, que mide la
     * antelacion de la cancelacion respecto a la cita.
     */
    private void validarQueEstaEnElFuturo(LocalDateTime fechaHora) {
        if (!fechaHora.isAfter(LocalDateTime.now(clock))) {
            throw new SolicitudInvalidaException(CodigoError.FECHA_EN_EL_PASADO,
                    "No se pueden agendar citas en el pasado.");
        }
    }

    /** RN-01: la cita debe empezar exactamente en un multiplo de 30 minutos. */
    private void validarAlineacionDeFranja(LocalDateTime fechaHora) {
        if (!calendario.esInicioDeFranja(fechaHora)) {
            throw new SolicitudInvalidaException(CodigoError.FRANJA_NO_VALIDA,
                    "La hora debe coincidir con el inicio de una franja de 30 minutos (por ejemplo 09:00 o 09:30).");
        }
    }

    /** RN-01: dia con atencion y hora dentro de la jornada de ese dia. */
    private void validarHorarioLaboral(LocalDateTime fechaHora) {
        LocalDate fecha = fechaHora.toLocalDate();

        if (!calendario.esDiaHabil(fecha)) {
            throw new SolicitudInvalidaException(CodigoError.FUERA_DE_HORARIO_LABORAL,
                    "El %s no hay atención.".formatted(fecha));
        }
        if (!calendario.estaDentroDeLaJornada(fechaHora)) {
            throw new SolicitudInvalidaException(CodigoError.FUERA_DE_HORARIO_LABORAL,
                    "La hora %s está fuera del horario de atención del %s. Franjas de ese día: de %s a %s."
                            .formatted(
                                    fechaHora.toLocalTime().format(FORMATO_HORA),
                                    fecha,
                                    primeraFranja(fecha),
                                    ultimaFranja(fecha)));
        }
    }

    /**
     * RN-03. La edad minima efectiva es 0: sin fecha de nacimiento se asume edad 0, que es
     * valida, y el enunciado no fija ninguna edad minima mayor ni maxima. Lo unico que se
     * rechaza es una fecha de nacimiento futura.
     *
     * <p>El registro ya lo impide con {@code @PastOrPresent}, asi que esta comprobacion es
     * una segunda linea de defensa para datos que hayan entrado por otra via (una carga
     * inicial, una migracion) y no deberia dispararse por trafico normal de la API.</p>
     */
    private void validarFechaDeNacimiento(Paciente paciente) {
        LocalDate nacimiento = paciente.getFechaNacimiento();
        if (nacimiento != null && nacimiento.isAfter(LocalDate.now(clock))) {
            throw new SolicitudInvalidaException(CodigoError.FECHA_NACIMIENTO_INVALIDA,
                    "El paciente tiene registrada una fecha de nacimiento futura; corrija sus datos antes de agendar.");
        }
    }

    /**
     * RN-05. Se comprueba antes que la ocupacion de las agendas a proposito: a un paciente
     * bloqueado no le sirve de nada enterarse de que ademas la franja estaba cogida, porque
     * ninguna otra franja le va a funcionar tampoco. El primer error que recibe debe ser el
     * unico sobre el que puede actuar.
     */
    private void validarQueNoEsteBloqueado(Paciente paciente) {
        EstadoDeBloqueo bloqueo = politicaDePenalizaciones.evaluarBloqueo(paciente.getId());
        if (bloqueo.bloqueado()) {
            throw new ConflictoDeNegocioException(CodigoError.PACIENTE_BLOQUEADO,
                    politicaDePenalizaciones.describirBloqueo(bloqueo));
        }
    }

    /** RN-02: solo las citas PROGRAMADAS ocupan la franja; una cancelada la libera. */
    private void validarAgendaDelMedico(Medico medico, LocalDateTime fechaHora) {
        if (citaRepository.existsByMedicoIdAndFechaHoraAndEstado(
                medico.getId(), fechaHora, EstadoCita.PROGRAMADA)) {
            throw new ConflictoDeNegocioException(CodigoError.MEDICO_NO_DISPONIBLE,
                    "El médico ya tiene una cita programada en esa franja.");
        }
    }

    /**
     * RN-04. <b>Ambiguedad resuelta:</b> el enunciado titula la regla como "mismo medico"
     * pero el texto sugiere que aplica tambien con otro. Se adopta la lectura amplia: un
     * paciente no puede tener dos citas programadas a la misma hora <i>con ningun medico</i>,
     * porque una persona no puede estar en dos consultas a la vez.
     */
    private void validarAgendaDelPaciente(Paciente paciente, LocalDateTime fechaHora) {
        if (citaRepository.existsByPacienteIdAndFechaHoraAndEstado(
                paciente.getId(), fechaHora, EstadoCita.PROGRAMADA)) {
            throw new ConflictoDeNegocioException(CodigoError.PACIENTE_NO_DISPONIBLE,
                    "El paciente ya tiene una cita programada en esa franja con otro médico.");
        }
    }

    private String primeraFranja(LocalDate fecha) {
        return calendario.franjasDe(fecha).getFirst().format(FORMATO_HORA);
    }

    private String ultimaFranja(LocalDate fecha) {
        return calendario.franjasDe(fecha).getLast().format(FORMATO_HORA);
    }
}
