package com.medisalud.agenda.controller;

import com.medisalud.agenda.domain.EstadoCita;
import com.medisalud.agenda.domain.FiltroDeCitas;
import com.medisalud.agenda.dto.CancelacionResponse;
import com.medisalud.agenda.dto.CitaResponse;
import com.medisalud.agenda.dto.CrearCitaRequest;
import com.medisalud.agenda.dto.ReprogramacionResponse;
import com.medisalud.agenda.dto.ReprogramarCitaRequest;
import com.medisalud.agenda.service.CitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    @Operation(
            summary = "Lista citas con filtros opcionales",
            description = """
                    Todos los filtros son opcionales y se combinan entre sí; sin ninguno devuelve
                    todas las citas. El rango de fechas cubre días completos: desde las 00:00 de
                    fechaInicio hasta las 23:59:59 de fechaFin, ambos inclusive. El resultado va
                    ordenado cronológicamente.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de citas"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Algún filtro tiene formato inválido o el rango de fechas está invertido")
    })
    public List<CitaResponse> listar(
            @Parameter(description = "Solo citas de este médico")
            @RequestParam(required = false) Long medicoId,

            @Parameter(description = "Solo citas de este paciente")
            @RequestParam(required = false) Long pacienteId,

            @Parameter(description = "Solo citas en este estado")
            @RequestParam(required = false) EstadoCita estado,

            @Parameter(description = "Primer día incluido, en formato ISO (2026-08-03)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,

            @Parameter(description = "Último día incluido, en formato ISO (2026-08-09)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        return citaService.listar(
                new FiltroDeCitas(medicoId, pacienteId, estado, fechaInicio, fechaFin));
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

    @PatchMapping("/{id}/reprogramar")
    @Operation(
            summary = "Reprograma una cita a otro horario",
            description = """
                    Cancela la cita original y crea una nueva en el horario indicado, enlazada a la
                    anterior por citaOrigenId (RN-06). La operación es atómica: si el nuevo horario
                    no está libre, la cita original se conserva intacta. Reprogramar con menos de 2
                    horas de antelación también penaliza (RN-05), pero un paciente bloqueado sí
                    puede reprogramar: lo que no puede es agendar citas nuevas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita reprogramada"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Falta la fecha, está desalineada, fuera de horario, en el pasado o es la que ya tenía"),
            @ApiResponse(responseCode = "404", description = "No existe una cita con ese id"),
            @ApiResponse(responseCode = "409", description = "La cita no está programada o el nuevo horario está ocupado")
    })
    public ReprogramacionResponse reprogramar(
            @PathVariable Long id, @Valid @RequestBody ReprogramarCitaRequest solicitud) {
        return citaService.reprogramar(id, solicitud);
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
