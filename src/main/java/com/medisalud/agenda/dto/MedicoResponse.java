package com.medisalud.agenda.dto;

import com.medisalud.agenda.domain.Medico;

/**
 * Representacion publica de un medico.
 *
 * <p>La conversion vive en una factoria estatica del propio DTO en lugar de en una clase
 * {@code Mapper} aparte o en MapStruct: es codigo comprobado por el compilador, sin
 * reflexion ni generacion, y la dependencia apunta hacia dentro (la capa de API conoce el
 * dominio, el dominio no conoce la API). Para un MVP con estas entidades, una clase de
 * mapeo adicional solo anadiria indireccion.</p>
 */
public record MedicoResponse(
        Long id,
        String nombreCompleto,
        String especialidad,
        String telefono,
        String email) {

    public static MedicoResponse desde(Medico medico) {
        return new MedicoResponse(
                medico.getId(),
                medico.getNombreCompleto(),
                medico.getEspecialidad(),
                medico.getTelefono(),
                medico.getEmail());
    }
}
