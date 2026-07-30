package com.medisalud.agenda.dto;

/**
 * Normalizacion de las cadenas que llegan en los DTOs de entrada.
 *
 * <p>Se aplica en el constructor compacto de cada record, antes de que corra Bean
 * Validation, de modo que las restricciones evaluan siempre el valor ya saneado.</p>
 */
final class Textos {

    private Textos() {
        // Clase de utilidad.
    }

    /**
     * Recorta los espacios sobrantes y convierte en {@code null} lo que quede vacio.
     *
     * <p>Sin esta normalizacion, {@code "   "} pasaria un {@code @Size(min = 3)} sin
     * aportar contenido alguno, y un campo opcional enviado como {@code ""} se
     * persistiria como cadena vacia en lugar de quedar como "no informado". Tratar
     * {@code ""} y {@code "   "} igual que la ausencia del campo hace que el contrato de
     * la API sea predecible para el cliente.</p>
     *
     * @param valor texto recibido, posiblemente nulo
     * @return el texto sin espacios en los extremos, o {@code null} si no queda contenido
     */
    static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
