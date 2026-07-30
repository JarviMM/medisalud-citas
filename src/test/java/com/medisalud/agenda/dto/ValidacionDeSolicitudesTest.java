package com.medisalud.agenda.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validaciones de los DTOs de entrada.
 *
 * <p>Se ejerce el {@link Validator} directamente, sin levantar Spring: son reglas
 * declarativas sobre objetos planos y no necesitan contexto, asi que la suite completa
 * corre en milisegundos.</p>
 */
class ValidacionDeSolicitudesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    @Nested
    @DisplayName("Registro de médico")
    class RegistroDeMedico {

        @Test
        @DisplayName("Una solicitud completa y correcta no produce errores")
        void solicitudValida() {
            assertThat(validator.validate(medicoValido())).isEmpty();
        }

        @Test
        @DisplayName("Teléfono y email son opcionales")
        void telefonoYEmailSonOpcionales() {
            var solicitud = new CrearMedicoRequest("Dra. María González", "Cardiología", null, null);

            assertThat(validator.validate(solicitud)).isEmpty();
        }

        @Test
        @DisplayName("El nombre debe tener entre 3 y 100 caracteres")
        void nombreFueraDeRango() {
            var corto = new CrearMedicoRequest("Ab", "Cardiología", null, null);
            var largo = new CrearMedicoRequest("N".repeat(101), "Cardiología", null, null);

            assertThat(camposConError(corto)).containsExactly("nombreCompleto");
            assertThat(camposConError(largo)).containsExactly("nombreCompleto");
        }

        @Test
        @DisplayName("Nombre y especialidad son obligatorios")
        void camposObligatorios() {
            var vacia = new CrearMedicoRequest(null, null, null, null);

            assertThat(camposConError(vacia))
                    .containsExactlyInAnyOrder("nombreCompleto", "especialidad");
        }

        @Test
        @DisplayName("El email debe tener formato válido")
        void emailConFormatoInvalido() {
            var solicitud = new CrearMedicoRequest(
                    "Dra. María González", "Cardiología", null, "correo-sin-arroba");

            assertThat(camposConError(solicitud)).containsExactly("email");
        }

        @Test
        @DisplayName("Los espacios se recortan y las cadenas vacías equivalen a campo no enviado")
        void normalizaEntradaDeTexto() {
            var solicitud = new CrearMedicoRequest(
                    "   Dra. María González   ", "  Cardiología  ", "   ", "");

            assertThat(solicitud.nombreCompleto()).isEqualTo("Dra. María González");
            assertThat(solicitud.especialidad()).isEqualTo("Cardiología");
            assertThat(solicitud.telefono()).isNull();
            assertThat(solicitud.email()).isNull();
            assertThat(validator.validate(solicitud)).isEmpty();
        }

        @Test
        @DisplayName("Un nombre de solo espacios se rechaza, no se cuela por @Size")
        void nombreDeSoloEspacios() {
            var solicitud = new CrearMedicoRequest("     ", "Cardiología", null, null);

            assertThat(camposConError(solicitud)).containsExactly("nombreCompleto");
        }
    }

    @Nested
    @DisplayName("Registro de paciente")
    class RegistroDePaciente {

        @Test
        @DisplayName("Una solicitud completa y correcta no produce errores")
        void solicitudValida() {
            assertThat(validator.validate(pacienteValido(LocalDate.of(1990, 5, 20)))).isEmpty();
        }

        @Test
        @DisplayName("La fecha de nacimiento es opcional (RN-03: equivale a edad 0)")
        void fechaDeNacimientoOpcional() {
            assertThat(validator.validate(pacienteValido(null))).isEmpty();
        }

        @Test
        @DisplayName("RN-03: una fecha de nacimiento futura se rechaza")
        void fechaDeNacimientoFutura() {
            var solicitud = pacienteValido(LocalDate.now().plusDays(1));

            assertThat(camposConError(solicitud)).containsExactly("fechaNacimiento");
            assertThat(mensajesDe(solicitud)).contains("La fecha de nacimiento no puede ser futura.");
        }

        @Test
        @DisplayName("Nacer hoy es válido")
        void nacidoHoy() {
            assertThat(validator.validate(pacienteValido(LocalDate.now()))).isEmpty();
        }

        @Test
        @DisplayName("Todos los campos salvo la fecha de nacimiento son obligatorios")
        void camposObligatorios() {
            var vacia = new CrearPacienteRequest(null, null, null, null, null);

            assertThat(camposConError(vacia)).containsExactlyInAnyOrder(
                    "nombreCompleto", "documentoIdentidad", "telefono", "email");
        }

        @Test
        @DisplayName("El documento de identidad debe tener al menos 7 caracteres")
        void documentoDemasiadoCorto() {
            var solicitud = new CrearPacienteRequest(
                    "Juan Pérez", "12345", "3001234567", "juan@example.com", null);

            assertThat(camposConError(solicitud)).containsExactly("documentoIdentidad");
        }

        @Test
        @DisplayName("El documento de identidad no admite caracteres arbitrarios")
        void documentoConCaracteresNoAdmitidos() {
            var solicitud = new CrearPacienteRequest(
                    "Juan Pérez", "1020' OR '1'='1", "3001234567", "juan@example.com", null);

            assertThat(camposConError(solicitud)).containsExactly("documentoIdentidad");
        }
    }

    @Nested
    @DisplayName("Validador de teléfono")
    class ValidadorDeTelefono {

        @ParameterizedTest(name = "\"{0}\" es válido")
        @ValueSource(strings = {"555-1001", "3001234567", "+57 (300) 123 4567", "1234567", "1.234.567"})
        void telefonosAceptados(String telefono) {
            var solicitud = new CrearMedicoRequest(
                    "Dra. María González", "Cardiología", telefono, null);

            assertThat(validator.validate(solicitud)).isEmpty();
        }

        @ParameterizedTest(name = "\"{0}\" es inválido")
        @ValueSource(strings = {"123456", "555-100", "---------", "abc1234567", "300 <script>"})
        void telefonosRechazados(String telefono) {
            var solicitud = new CrearMedicoRequest(
                    "Dra. María González", "Cardiología", telefono, null);

            assertThat(camposConError(solicitud)).containsExactly("telefono");
        }
    }

    // ------------------------------------------------------------------ apoyo

    private static CrearMedicoRequest medicoValido() {
        return new CrearMedicoRequest(
                "Dra. María González", "Cardiología", "555-1001", "maria.gonzalez@medisalud.com");
    }

    private static CrearPacienteRequest pacienteValido(LocalDate fechaNacimiento) {
        return new CrearPacienteRequest(
                "Juan Pérez", "1020304050", "3001234567", "juan.perez@example.com", fechaNacimiento);
    }

    private static Set<String> camposConError(Object solicitud) {
        return validator.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> mensajesDe(Object solicitud) {
        return validator.validate(solicitud).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
