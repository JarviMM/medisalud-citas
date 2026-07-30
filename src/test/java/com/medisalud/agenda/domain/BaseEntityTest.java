package com.medisalud.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Contrato de igualdad de las entidades.
 *
 * <p>{@code equals} y {@code hashCode} estan escritos a mano y son de los pocos sitios donde
 * un error no se manifiesta como una excepcion sino como datos que desaparecen de una
 * coleccion. De ahi que se prueben aparte y en sus casos limite.</p>
 */
class BaseEntityTest {

    @Test
    @DisplayName("Una entidad es igual a sí misma")
    void igualASiMisma() {
        Medico medico = conId(unMedico(), 1L);

        assertThat(medico).isEqualTo(medico);
    }

    @Test
    @DisplayName("Dos entidades del mismo tipo con el mismo identificador son la misma")
    void mismoTipoMismoId() {
        assertThat(conId(unMedico(), 1L)).isEqualTo(conId(unMedico(), 1L));
    }

    @Test
    @DisplayName("Identificadores distintos son entidades distintas")
    void identificadoresDistintos() {
        assertThat(conId(unMedico(), 1L)).isNotEqualTo(conId(unMedico(), 2L));
    }

    @Test
    @DisplayName("Tipos distintos con el mismo identificador no son iguales")
    void tiposDistintos() {
        // Sin comparar el tipo, el médico 1 y el paciente 1 serían el mismo objeto
        // dentro de cualquier colección que los mezclara.
        assertThat((Object) conId(unMedico(), 1L)).isNotEqualTo(conId(unPaciente(), 1L));
    }

    @Test
    @DisplayName("Una entidad sin persistir solo es igual a sí misma")
    void transitoriaSoloIgualASiMisma() {
        Medico sinId = unMedico();
        Medico otroSinId = unMedico();

        assertThat(sinId).isEqualTo(sinId);
        // Aunque tengan exactamente los mismos datos: todavía no son "la misma fila".
        assertThat(sinId).isNotEqualTo(otroSinId);
    }

    @Test
    @DisplayName("Una entidad no es igual a null")
    void distintaDeNull() {
        assertThat(conId(unMedico(), 1L)).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Guardar la entidad no la pierde dentro de un HashSet")
    void elHashCodeSobreviveALaAsignacionDeId() {
        Medico medico = unMedico();
        Set<Medico> coleccion = new HashSet<>();
        coleccion.add(medico);

        // Simula lo que hace JPA al persistir: asigna el identificador generado.
        ReflectionTestUtils.setField(medico, "id", 1L);

        // Con un hashCode derivado del id, la entidad habría cambiado de bucket y
        // contains() diría que no está aunque el propio Set la tenga dentro.
        assertThat(coleccion).contains(medico);
        assertThat(coleccion.iterator().next()).isSameAs(medico);
    }

    private static Medico unMedico() {
        return Medico.builder()
                .nombreCompleto("Dra. María González")
                .especialidad("Cardiología")
                .build();
    }

    private static Paciente unPaciente() {
        return Paciente.builder()
                .nombreCompleto("Juan Pérez")
                .documentoIdentidad("1020304050")
                .telefono("3001234567")
                .email("juan.perez@example.com")
                .build();
    }

    private static <T extends BaseEntity> T conId(T entidad, Long id) {
        ReflectionTestUtils.setField(entidad, "id", id);
        return entidad;
    }
}
