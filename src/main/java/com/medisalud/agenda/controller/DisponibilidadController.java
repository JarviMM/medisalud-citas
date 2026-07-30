package com.medisalud.agenda.controller;

import com.medisalud.agenda.dto.DisponibilidadResponse;
import com.medisalud.agenda.service.DisponibilidadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de franjas libres de un medico (RF-04).
 *
 * <p>Vive en su propio controller y no dentro de {@code MedicoController} aunque la ruta
 * cuelgue de {@code /api/medicos}: es un recurso derivado, con su propio servicio y su
 * propio caso de uso. Mezclarlo con el CRUD del medico haria que esa clase dependiera de
 * dos servicios sin relacion entre si.</p>
 */
@RestController
@RequestMapping("/api/medicos/{medicoId}/disponibilidad")
@RequiredArgsConstructor
@Tag(name = "Disponibilidad", description = "Franjas libres de un médico")
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    /**
     * Las fechas se reciben como {@code LocalDate} en formato ISO ({@code 2026-08-03}) y no
     * como instantes: la unidad natural de una agenda es el dia, y las horas concretas las
     * determina el horario laboral, no el cliente.
     */
    @GetMapping
    @Operation(
            summary = "Consulta las franjas libres de un médico",
            description = """
                    Devuelve las franjas de 30 minutos libres entre las dos fechas, ambas inclusive.
                    Se omiten los días sin atención (domingos y festivos), las franjas ya reservadas
                    y las que ya pasaron. Para reservar, componga la fechaHora como fecha + "T" + franja.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disponibilidad calculada"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Faltan las fechas, tienen formato inválido, el rango está invertido o excede el máximo"),
            @ApiResponse(responseCode = "404", description = "No existe un médico con ese id")
    })
    public DisponibilidadResponse consultar(
            @PathVariable Long medicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return disponibilidadService.calcular(medicoId, fechaInicio, fechaFin);
    }
}
