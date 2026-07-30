package com.medisalud.agenda.dto;

import com.medisalud.agenda.validator.TelefonoValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada para registrar un medico.
 *
 * <p>Es un {@code record} y no una clase con Lombok: un DTO de entrada es un portador de
 * datos inmutable sin identidad propia, justo lo que un record modela de forma nativa en
 * Java 21, con {@code equals}, {@code hashCode} y {@code toString} correctos y sin
 * generacion de codigo.</p>
 *
 * <p>Los mensajes de validacion se declaran explicitamente en espanol en lugar de dejar
 * los de Hibernate Validator: los mensajes por defecto se resuelven segun el
 * {@code Locale} de la peticion, asi que el mismo error responderia en un idioma distinto
 * segun la cabecera {@code Accept-Language} del cliente. Fijarlos aqui convierte el
 * mensaje en parte estable del contrato de la API.</p>
 */
public record CrearMedicoRequest(

        @NotBlank(message = "El nombre completo es obligatorio.")
        @Size(min = 3, max = 100, message = "El nombre completo debe tener entre 3 y 100 caracteres.")
        String nombreCompleto,

        @NotBlank(message = "La especialidad es obligatoria.")
        @Size(max = 80, message = "La especialidad no puede superar los 80 caracteres.")
        String especialidad,

        /* Opcional: si no se envia queda nulo y no se valida su contenido. */
        @TelefonoValido
        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres.")
        String telefono,

        /* Opcional. */
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres.")
        String email) {

    public CrearMedicoRequest {
        nombreCompleto = Textos.normalizar(nombreCompleto);
        especialidad = Textos.normalizar(especialidad);
        telefono = Textos.normalizar(telefono);
        email = Textos.normalizar(email);
    }
}
