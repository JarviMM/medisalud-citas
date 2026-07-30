package com.medisalud.agenda.validator;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Valida que un telefono contenga al menos 7 digitos y ningun caracter extrano.
 *
 * <p>La regla del enunciado ("minimo 7 digitos") no se puede expresar con
 * {@code @Size}, que cuenta caracteres y no digitos: {@code "555-1001"} tiene 8
 * caracteres pero 7 digitos, y {@code "---------"} tiene 9 caracteres y ninguno. Un
 * {@code @Pattern} capaz de contar digitos intercalados con separadores resulta
 * ilegible, asi que la regla se encapsula en un validador propio reutilizable por
 * cualquier DTO que lleve telefono (DRY).</p>
 *
 * <p>Un valor {@code null} se considera valido: la obligatoriedad es responsabilidad de
 * {@code @NotBlank}, no de esta anotacion. Asi el mismo constraint sirve para el telefono
 * opcional del medico y para el obligatorio del paciente.</p>
 */
@Documented
@Constraint(validatedBy = TelefonoValidador.class)
@Target({FIELD, METHOD, PARAMETER, CONSTRUCTOR, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface TelefonoValido {

    String message() default "El teléfono debe contener al menos 7 dígitos y solo admite dígitos, espacios y los símbolos + - ( ) .";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
