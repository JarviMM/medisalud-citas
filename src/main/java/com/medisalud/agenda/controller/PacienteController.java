package com.medisalud.agenda.controller;

import com.medisalud.agenda.dto.CrearPacienteRequest;
import com.medisalud.agenda.dto.PacienteResponse;
import com.medisalud.agenda.service.PacienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de pacientes.
 */
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Registro y consulta de pacientes")
public class PacienteController {

    private final PacienteService pacienteService;

    @PostMapping
    @Operation(
            summary = "Registra un paciente",
            description = "El documento de identidad debe ser único en el sistema.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Paciente registrado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "El documento de identidad ya está registrado")
    })
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody CrearPacienteRequest solicitud) {
        PacienteResponse creado = pacienteService.crear(solicitud);
        return ResponseEntity.created(Ubicaciones.delRecursoCreado(creado.id())).body(creado);
    }

    @GetMapping
    @Operation(summary = "Lista todos los pacientes", description = "Ordenados por nombre completo.")
    public List<PacienteResponse> listar() {
        return pacienteService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un paciente por su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paciente encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe un paciente con ese id")
    })
    public PacienteResponse obtener(@PathVariable Long id) {
        return pacienteService.obtenerPorId(id);
    }
}
