package com.medisalud.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.medisalud.agenda.domain.Paciente;
import com.medisalud.agenda.dto.CrearPacienteRequest;
import com.medisalud.agenda.dto.PacienteResponse;
import com.medisalud.agenda.exception.CodigoError;
import com.medisalud.agenda.exception.ConflictoDeNegocioException;
import com.medisalud.agenda.exception.ExcepcionDeDominio;
import com.medisalud.agenda.exception.RecursoNoEncontradoException;
import com.medisalud.agenda.repository.PacienteRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unicidad del documento de identidad en {@link PacienteService}.
 *
 * <p>Interesa sobre todo la doble defensa: la comprobacion previa da un mensaje concreto, y
 * el {@code catch} sobre la escritura cubre la ventana entre esa comprobacion y el INSERT.
 * El segundo camino es imposible de provocar desde un test de integracion sin concurrencia
 * real, asi que se ejerce aqui.</p>
 */
@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    private static final String MENSAJE_DUPLICADO =
            "Ya existe un paciente registrado con ese documento de identidad.";

    @Mock private PacienteRepository pacienteRepository;

    @InjectMocks private PacienteService servicio;

    @Test
    @DisplayName("Registra al paciente cuando el documento está libre")
    void registroCorrecto() {
        given(pacienteRepository.existsByDocumentoIdentidad("1020304050")).willReturn(false);
        given(pacienteRepository.saveAndFlush(any())).willAnswer(i -> i.getArgument(0));

        PacienteResponse respuesta = servicio.crear(unaSolicitud());

        assertThat(respuesta.documentoIdentidad()).isEqualTo("1020304050");
        assertThat(respuesta.nombreCompleto()).isEqualTo("Juan Pérez");
        assertThat(respuesta.fechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 20));
    }

    @Test
    @DisplayName("Un documento ya registrado se rechaza sin llegar a escribir")
    void documentoYaRegistrado() {
        given(pacienteRepository.existsByDocumentoIdentidad("1020304050")).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(unaSolicitud()))
                .isInstanceOf(ConflictoDeNegocioException.class)
                .hasMessage(MENSAJE_DUPLICADO)
                .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                .isEqualTo(CodigoError.DOCUMENTO_DUPLICADO);

        verify(pacienteRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Si otra petición se adelanta entre la comprobación y el INSERT, el resultado es el mismo 409")
    void colisionConcurrente() {
        given(pacienteRepository.existsByDocumentoIdentidad("1020304050")).willReturn(false);
        given(pacienteRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_paciente_documento_identidad"));

        assertThatThrownBy(() -> servicio.crear(unaSolicitud()))
                .isInstanceOf(ConflictoDeNegocioException.class)
                // El cliente ve exactamente lo mismo gane quien gane la carrera.
                .hasMessage(MENSAJE_DUPLICADO)
                .extracting(ex -> ((ExcepcionDeDominio) ex).getCodigo())
                .isEqualTo(CodigoError.DOCUMENTO_DUPLICADO);
    }

    @Test
    @DisplayName("El mensaje de conflicto nunca repite el documento recibido")
    void noFiltraElDocumento() {
        given(pacienteRepository.existsByDocumentoIdentidad("1020304050")).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(unaSolicitud()))
                // Repetirlo permitiría comprobar desde fuera si una persona está registrada.
                .hasMessageNotContaining("1020304050");
    }

    @Test
    @DisplayName("Buscar un paciente inexistente produce un 404 con el identificador pedido")
    void pacienteInexistente() {
        given(pacienteRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.buscarEntidad(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró el paciente con id 99.");
    }

    @Test
    @DisplayName("Buscar un paciente existente devuelve la entidad")
    void pacienteExistente() {
        Paciente paciente = Paciente.builder()
                .id(2L)
                .nombreCompleto("Juan Pérez")
                .documentoIdentidad("1020304050")
                .telefono("3001234567")
                .email("juan.perez@example.com")
                .build();
        given(pacienteRepository.findById(2L)).willReturn(Optional.of(paciente));

        assertThat(servicio.buscarEntidad(2L)).isSameAs(paciente);
    }

    private static CrearPacienteRequest unaSolicitud() {
        return new CrearPacienteRequest(
                "Juan Pérez", "1020304050", "3001234567", "juan.perez@example.com",
                LocalDate.of(1990, 5, 20));
    }
}
