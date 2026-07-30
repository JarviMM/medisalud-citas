package com.medisalud.agenda.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Implementacion de {@link TelefonoValido}.
 *
 * <p>Comprueba dos cosas por separado para que la intencion quede explicita: que el texto
 * solo contenga caracteres propios de un telefono, y que entre ellos haya al menos el
 * numero minimo de digitos exigido.</p>
 */
public class TelefonoValidador implements ConstraintValidator<TelefonoValido, String> {

    /** Minimo exigido por el enunciado. */
    private static final int DIGITOS_MINIMOS = 7;

    /** Digitos y los separadores habituales de un numero telefonico. */
    private static final Pattern CARACTERES_ADMITIDOS = Pattern.compile("[0-9+()\\-. ]+");

    @Override
    public boolean isValid(String telefono, ConstraintValidatorContext contexto) {
        // La ausencia de valor la controla @NotBlank cuando el campo es obligatorio.
        if (telefono == null) {
            return true;
        }
        if (!CARACTERES_ADMITIDOS.matcher(telefono).matches()) {
            return false;
        }
        return telefono.chars().filter(Character::isDigit).count() >= DIGITOS_MINIMOS;
    }
}
