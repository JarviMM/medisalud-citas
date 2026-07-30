package com.medisalud.agenda.dto;

import com.medisalud.agenda.validator.TelefonoValido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Datos de entrada para registrar un paciente.
 *
 * <p>Sobre {@code fechaNacimiento} y la RN-03: el campo es opcional, pero si se envia no
 * puede ser futura. {@code @PastOrPresent} y no {@code @Past} porque un recien nacido
 * registrado el mismo dia de su nacimiento es un caso legitimo. No se impone edad minima
 * ni maxima: el enunciado no las pide y la ausencia de fecha equivale a edad 0, que es
 * valida para agendar.</p>
 */
public record CrearPacienteRequest(

        @Schema(example = "Juan Pérez")
        @NotBlank(message = "El nombre completo es obligatorio.")
        @Size(min = 3, max = 100, message = "El nombre completo debe tener entre 3 y 100 caracteres.")
        String nombreCompleto,

        @Schema(example = "1020304050", description = "Único en el sistema.")
        @NotBlank(message = "El documento de identidad es obligatorio.")
        @Size(min = 7, max = 30, message = "El documento de identidad debe tener entre 7 y 30 caracteres.")
        @Pattern(
                regexp = "[A-Za-z0-9.\\-]+",
                message = "El documento de identidad solo admite letras, dígitos, puntos y guiones.")
        String documentoIdentidad,

        @Schema(example = "3001234567", description = "Al menos 7 dígitos.")
        @NotBlank(message = "El teléfono es obligatorio.")
        @TelefonoValido
        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres.")
        String telefono,

        @Schema(example = "juan.perez@example.com")
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres.")
        String email,

        /* Opcional. Ver RN-03 en el javadoc del record. */
        @Schema(
                example = "1990-05-20",
                description = "Opcional (RN-03). Si se omite se asume edad 0, que es válida. No puede ser futura.")
        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura.")
        LocalDate fechaNacimiento) {

    public CrearPacienteRequest {
        nombreCompleto = Textos.normalizar(nombreCompleto);
        documentoIdentidad = Textos.normalizar(documentoIdentidad);
        telefono = Textos.normalizar(telefono);
        email = Textos.normalizar(email);
    }
}
