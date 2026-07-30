package com.medisalud.agenda.controller;

import com.medisalud.agenda.dto.CrearMedicoRequest;
import com.medisalud.agenda.dto.MedicoResponse;
import com.medisalud.agenda.service.MedicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints REST de medicos.
 *
 * <p>El controller no contiene una sola decision de negocio: valida la forma de la entrada
 * con {@code @Valid}, delega en el servicio y decide el codigo HTTP. Ni siquiera comprueba
 * si el medico existe; de eso se encarga el servicio lanzando la excepcion de dominio que
 * el manejador global traduce a 404.</p>
 */
@RestController
@RequestMapping("/api/medicos")
@RequiredArgsConstructor
@Tag(name = "Médicos", description = "Registro y consulta de médicos")
public class MedicoController {

    private final MedicoService medicoService;

    @PostMapping
    @Operation(
            summary = "Registra un médico",
            description = "Devuelve 201 con la cabecera Location apuntando al recurso creado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Médico registrado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    public ResponseEntity<MedicoResponse> crear(@Valid @RequestBody CrearMedicoRequest solicitud) {
        MedicoResponse creado = medicoService.crear(solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @GetMapping
    @Operation(summary = "Lista todos los médicos", description = "Ordenados por nombre completo.")
    public List<MedicoResponse> listar() {
        return medicoService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un médico por su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Médico encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe un médico con ese id")
    })
    public MedicoResponse obtener(@PathVariable Long id) {
        return medicoService.obtenerPorId(id);
    }
}
