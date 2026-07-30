package com.medisalud.agenda.config;

import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.repository.MedicoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precarga los tres medicos de ejemplo al arrancar la aplicacion.
 *
 * <p><b>Por que un {@code CommandLineRunner} y no {@code data.sql}:</b></p>
 * <ul>
 *   <li>Los datos pasan por el mismo mapeo JPA que el resto de la aplicacion, asi que un
 *       cambio en las entidades rompe la compilacion en vez de fallar en runtime con un
 *       SQL desalineado.</li>
 *   <li>{@code data.sql} obliga a coordinar su ejecucion con {@code ddl-auto} mediante
 *       {@code spring.jpa.defer-datasource-initialization}, un detalle facil de romper.</li>
 *   <li>Permite que la carga sea idempotente y condicional, algo que un script plano no
 *       puede expresar sin SQL especifico del motor.</li>
 *   <li>Al migrar a PostgreSQL el codigo sigue siendo valido sin reescribir SQL.</li>
 * </ul>
 *
 * <p>Se excluye del perfil {@code test} para que cada test controle su propio conjunto de
 * datos y las aserciones sobre listados no dependan de estos registros.</p>
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class CargaInicialMedicos implements CommandLineRunner {

    private final MedicoRepository medicoRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (medicoRepository.count() > 0) {
            log.info("Carga inicial omitida: ya existen medicos registrados.");
            return;
        }

        List<Medico> medicos = List.of(
                Medico.builder()
                        .nombreCompleto("Dra. María González")
                        .especialidad("Cardiología")
                        .telefono("555-1001")
                        .email("maria.gonzalez@medisalud.com")
                        .build(),
                Medico.builder()
                        .nombreCompleto("Dr. Carlos Ruiz")
                        .especialidad("Pediatría")
                        .telefono("555-1002")
                        .email("carlos.ruiz@medisalud.com")
                        .build(),
                Medico.builder()
                        .nombreCompleto("Dra. Ana López")
                        .especialidad("Dermatología")
                        .telefono("555-1003")
                        .email("ana.lopez@medisalud.com")
                        .build());

        medicoRepository.saveAll(medicos);
        log.info("Carga inicial completada: {} medicos precargados.", medicos.size());
    }
}
