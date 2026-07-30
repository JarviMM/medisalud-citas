package com.medisalud.agenda.controller;

import java.net.URI;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Construccion de la cabecera {@code Location} de los recursos recien creados.
 *
 * <p>Los tres endpoints de creacion repetian el mismo bloque de cuatro lineas. Ademas de
 * ser duplicacion, era duplicacion del tipo peligroso: si uno de ellos hubiera usado
 * {@code fromCurrentRequest()} en vez de {@code fromCurrentRequestUri()}, ese endpoint
 * habria arrastrado los parametros de consulta a la cabecera y nadie lo habria notado,
 * porque cada copia se lee por separado.</p>
 */
final class Ubicaciones {

    private Ubicaciones() {
        // Clase de utilidad.
    }

    /**
     * URI del recurso creado por la peticion en curso, colgando su identificador de la ruta
     * a la que se hizo el POST.
     *
     * <p>Se parte de {@code fromCurrentRequestUri()} y no de {@code fromCurrentRequest()}
     * para que unos parametros de consulta en el POST no acaben dentro de la cabecera.</p>
     *
     * @param id identificador del recurso recien creado
     */
    static URI delRecursoCreado(Object id) {
        return ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
