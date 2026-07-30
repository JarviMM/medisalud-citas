package com.medisalud.agenda.controller;

import com.medisalud.agenda.dto.CancelacionResponse;
import com.medisalud.agenda.dto.CitaResponse;
import com.medisalud.agenda.dto.CrearCitaRequest;
import com.medisalud.agenda.service.CitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints REST de citas.
 */
@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
@Tag(name = "Citas", description = "Reserva y consulta de citas médicas")
public class CitaController {

    private final CitaService citaService;

    @PostMapping
    @Operation(
            summary = "Reserva una cita",
            description = """
                    Aplica las reglas de agendamiento: horario laboral y franjas de 30 minutos (RN-01),
                    agenda libre del médico (RN-02) y del paciente en esa misma franja con cualquier
                    médico (RN-04).""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cita reservada"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Entrada inválida, franja desalineada, fuera de horario laboral o fecha pasada"),
            @ApiResponse(responseCode = "404", description = "El médico o el paciente no existen"),
            @ApiResponse(responseCode = "409", description = "La franja ya está ocupada para el médico o el paciente")
    })
    public ResponseEntity<CitaResponse> reservar(@Valid @RequestBody CrearCitaRequest solicitud) {
        CitaResponse creada = citaService.reservar(solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(creada.id())
                .toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(
            summary = "Cancela una cita",
            description = """
                    Cancelar con menos de 2 horas de antelación registra una penalización para el
                    paciente (RN-05). La respuesta indica si se penalizó, cuántas penalizaciones
                    vigentes acumula y, si acaba de quedar bloqueado, desde cuándo podrá volver a
                    agendar. Cancelar con 2 horas exactas o más no penaliza.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita cancelada"),
            @ApiResponse(responseCode = "404", description = "No existe una cita con ese id"),
            @ApiResponse(responseCode = "409", description = "La cita ya estaba cancelada o atendida")
    })
    public CancelacionResponse cancelar(@PathVariable Long id) {
        return citaService.cancelar(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una cita por su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe una cita con ese id")
    })
    public CitaResponse obtener(@PathVariable Long id) {
        return citaService.obtenerPorId(id);
    }
}
