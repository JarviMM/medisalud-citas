package com.medisalud.agenda.repository;

import com.medisalud.agenda.domain.Cita;
import com.medisalud.agenda.domain.FiltroDeCitas;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Traduce un {@link FiltroDeCitas} a la consulta que lo resuelve (RF-06).
 *
 * <p><b>Por que la API de criterios y no consultas derivadas:</b> cinco filtros opcionales
 * dan 32 combinaciones. Con metodos derivados habria que declarar una firma por cada una, o
 * concatenar JPQL a mano, que es exactamente como se introduce una inyeccion. Aqui cada
 * criterio se anade como un predicado y JPA liga los valores como parametros preparados: la
 * entrada del usuario nunca toca el texto de la consulta.</p>
 *
 * <p>Los filtros ausentes sencillamente no aportan predicado. Con la lista vacia,
 * {@code and()} devuelve una conjuncion siempre cierta, asi que una peticion sin filtros
 * lista todas las citas sin ningun caso especial.</p>
 *
 * <p>Los nombres de atributo van en constantes en lugar de repetirse como literales. La
 * alternativa con garantia en tiempo de compilacion seria el metamodelo estatico de JPA
 * ({@code Cita_}), que se descarto para no anadir un procesador de anotaciones mas al
 * lado de Lombok y mantener la construccion del proyecto sin sorpresas.</p>
 */
public final class EspecificacionesDeCita {

    private static final String MEDICO = "medico";
    private static final String PACIENTE = "paciente";
    private static final String ESTADO = "estado";
    private static final String FECHA_HORA = "fechaHora";
    private static final String ID = "id";

    private EspecificacionesDeCita() {
        // Clase de utilidad.
    }

    public static Specification<Cita> segun(FiltroDeCitas filtro) {
        return (raiz, consulta, constructor) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (filtro.medicoId() != null) {
                predicados.add(constructor.equal(raiz.get(MEDICO).get(ID), filtro.medicoId()));
            }
            if (filtro.pacienteId() != null) {
                predicados.add(constructor.equal(raiz.get(PACIENTE).get(ID), filtro.pacienteId()));
            }
            if (filtro.estado() != null) {
                predicados.add(constructor.equal(raiz.get(ESTADO), filtro.estado()));
            }
            if (filtro.fechaInicio() != null) {
                predicados.add(constructor.greaterThanOrEqualTo(
                        raiz.get(FECHA_HORA), filtro.fechaInicio().atStartOfDay()));
            }
            if (filtro.fechaFin() != null) {
                // Limite superior exclusivo sobre el dia siguiente: incluye el ultimo dia
                // completo sin tener que escribir 23:59:59 y sin perder ninguna cita.
                predicados.add(constructor.lessThan(
                        raiz.get(FECHA_HORA), filtro.fechaFin().plusDays(1).atStartOfDay()));
            }

            return constructor.and(predicados.toArray(Predicate[]::new));
        };
    }
}
