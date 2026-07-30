package com.medisalud.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medisalud.agenda.repository.CitaRepository;
import com.medisalud.agenda.repository.MedicoRepository;
import com.medisalud.agenda.repository.PacienteRepository;
import com.medisalud.agenda.repository.PenalizacionPacienteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica que el modelo de datos del paso 1 se mapea y persiste como se espera.
 *
 * <p>Se desactiva la sustitucion automatica del datasource ({@code Replace.NONE}) para
 * que el test corra contra la H2 declarada en el perfil {@code test} y no contra una base
 * implicita distinta a la del resto de la suite.</p>
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ModeloPersistenciaTest {

    private static final LocalDateTime FRANJA = LocalDateTime.of(2026, 8, 3, 9, 0);

    @Autowired private MedicoRepository medicoRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private PenalizacionPacienteRepository penalizacionRepository;

    @Test
    @DisplayName("Persiste el grafo completo medico - paciente - cita - penalizacion")
    void persisteGrafoCompleto() {
        Medico medico = medicoRepository.save(unMedico());
        Paciente paciente = pacienteRepository.save(unPaciente("1020304050"));

        Cita cita = citaRepository.save(citaProgramada(medico, paciente, FRANJA));

        PenalizacionPaciente penalizacion = penalizacionRepository.save(PenalizacionPaciente.builder()
                .paciente(paciente)
                .cita(cita)
                .fechaRegistro(FRANJA.minusMinutes(45))
                .motivo("Cancelación con 45 minutos de antelación")
                .build());

        assertThat(medico.getId()).isNotNull();
        assertThat(paciente.getId()).isNotNull();
        assertThat(cita.getId()).isNotNull();
        assertThat(penalizacion.getId()).isNotNull();

        assertThat(citaRepository.findById(cita.getId()))
                .get()
                .satisfies(recuperada -> {
                    assertThat(recuperada.getMedico().getNombreCompleto()).isEqualTo("Dra. María González");
                    assertThat(recuperada.getPaciente().getDocumentoIdentidad()).isEqualTo("1020304050");
                    assertThat(recuperada.getEstado()).isEqualTo(EstadoCita.PROGRAMADA);
                    assertThat(recuperada.getFechaCancelacion()).isNull();
                    assertThat(recuperada.getCitaOrigenId()).isNull();
                });
    }

    @Test
    @DisplayName("El documento de identidad es único a nivel de esquema")
    void documentoIdentidadEsUnico() {
        pacienteRepository.saveAndFlush(unPaciente("9999999"));

        assertThatThrownBy(() -> pacienteRepository.saveAndFlush(unPaciente("9999999")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Las consultas de solapamiento solo consideran citas PROGRAMADAS")
    void soloLasCitasProgramadasOcupanLaFranja() {
        Medico medico = medicoRepository.save(unMedico());
        Paciente paciente = pacienteRepository.save(unPaciente("1111111"));

        Cita cancelada = citaRepository.saveAndFlush(citaProgramada(medico, paciente, FRANJA));
        cancelada.cancelar(FRANJA.minusHours(5));
        citaRepository.saveAndFlush(cancelada);

        assertThat(citaRepository.existsByMedicoIdAndFechaHoraAndEstado(
                medico.getId(), FRANJA, EstadoCita.PROGRAMADA)).isFalse();
        assertThat(citaRepository.existsByPacienteIdAndFechaHoraAndEstado(
                paciente.getId(), FRANJA, EstadoCita.PROGRAMADA)).isFalse();
        assertThat(cancelada.getFechaCancelacion()).isEqualTo(FRANJA.minusHours(5));
    }

    /**
     * La comprobacion previa en el servicio deja una ventana de carrera entre dos
     * peticiones simultaneas. Estos tests verifican la red de seguridad: la unicidad de
     * franja impuesta por la propia base de datos, y que solo afecta a las citas vigentes.
     */
    @Nested
    @DisplayName("Unicidad de franja garantizada por el esquema (RN-02 y RN-04)")
    class UnicidadDeFranja {

        @Test
        @DisplayName("RN-02: un médico no puede tener dos citas PROGRAMADAS en la misma franja")
        void medicoNoAdmiteDosCitasProgramadasEnLaMismaFranja() {
            Medico medico = medicoRepository.save(unMedico());
            Paciente primero = pacienteRepository.save(unPaciente("101"));
            Paciente segundo = pacienteRepository.save(unPaciente("102"));

            citaRepository.saveAndFlush(citaProgramada(medico, primero, FRANJA));

            assertThatThrownBy(() ->
                    citaRepository.saveAndFlush(citaProgramada(medico, segundo, FRANJA)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("RN-04: un paciente no puede tener dos citas PROGRAMADAS en la misma franja, ni con médicos distintos")
        void pacienteNoAdmiteDosCitasProgramadasEnLaMismaFranja() {
            Medico cardiologa = medicoRepository.save(unMedico());
            Medico pediatra = medicoRepository.save(otroMedico());
            Paciente paciente = pacienteRepository.save(unPaciente("201"));

            citaRepository.saveAndFlush(citaProgramada(cardiologa, paciente, FRANJA));

            assertThatThrownBy(() ->
                    citaRepository.saveAndFlush(citaProgramada(pediatra, paciente, FRANJA)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Cancelar libera la franja para una cita nueva")
        void cancelarLiberaLaFranja() {
            Medico medico = medicoRepository.save(unMedico());
            Paciente primero = pacienteRepository.save(unPaciente("301"));
            Paciente segundo = pacienteRepository.save(unPaciente("302"));

            Cita original = citaRepository.saveAndFlush(citaProgramada(medico, primero, FRANJA));
            original.cancelar(FRANJA.minusHours(3));
            // El flush explícito fuerza el UPDATE que libera la franja antes del INSERT
            // que la reclama: Hibernate ordena los INSERT primero dentro de un mismo flush.
            citaRepository.saveAndFlush(original);

            assertThatCode(() -> citaRepository.saveAndFlush(citaProgramada(medico, segundo, FRANJA)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Varias citas canceladas conviven sobre la misma franja")
        void lasCitasCanceladasNoCompitenPorLaFranja() {
            Medico medico = medicoRepository.save(unMedico());
            Paciente paciente = pacienteRepository.save(unPaciente("401"));

            for (int intento = 0; intento < 3; intento++) {
                Cita cita = citaRepository.saveAndFlush(citaProgramada(medico, paciente, FRANJA));
                cita.cancelar(FRANJA.minusHours(4));
                citaRepository.saveAndFlush(cita);
            }

            assertThat(citaRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("Dos médicos distintos pueden atender la misma franja")
        void medicosDistintosCompartenFranja() {
            Medico cardiologa = medicoRepository.save(unMedico());
            Medico pediatra = medicoRepository.save(otroMedico());
            Paciente primero = pacienteRepository.save(unPaciente("501"));
            Paciente segundo = pacienteRepository.save(unPaciente("502"));

            citaRepository.saveAndFlush(citaProgramada(cardiologa, primero, FRANJA));

            assertThatCode(() -> citaRepository.saveAndFlush(citaProgramada(pediatra, segundo, FRANJA)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Edad del paciente (RN-03)")
    class EdadDelPaciente {

        @Test
        @DisplayName("Sin fecha de nacimiento se asume edad 0, que es válida")
        void sinFechaDeNacimientoLaEdadEsCero() {
            assertThat(unPaciente("2222222").edadEn(LocalDate.of(2026, 7, 30))).isZero();
        }

        @Test
        @DisplayName("Con fecha de nacimiento se calculan los años cumplidos")
        void calculaAniosCumplidos() {
            Paciente paciente = unPaciente("3333333");
            paciente.setFechaNacimiento(LocalDate.of(1990, 12, 31));

            assertThat(paciente.edadEn(LocalDate.of(2026, 7, 30))).isEqualTo(35);
            assertThat(paciente.edadEn(LocalDate.of(2026, 12, 31))).isEqualTo(36);
        }
    }

    private static Medico unMedico() {
        return Medico.builder()
                .nombreCompleto("Dra. María González")
                .especialidad("Cardiología")
                .telefono("555-1001")
                .email("maria.gonzalez@medisalud.com")
                .build();
    }

    private static Medico otroMedico() {
        return Medico.builder()
                .nombreCompleto("Dr. Carlos Ruiz")
                .especialidad("Pediatría")
                .telefono("555-1002")
                .email("carlos.ruiz@medisalud.com")
                .build();
    }

    private static Paciente unPaciente(String documento) {
        return Paciente.builder()
                .nombreCompleto("Juan Pérez")
                .documentoIdentidad(documento)
                .telefono("3001234567")
                .email("juan.perez@example.com")
                .build();
    }

    private static Cita citaProgramada(Medico medico, Paciente paciente, LocalDateTime fechaHora) {
        return Cita.builder()
                .medico(medico)
                .paciente(paciente)
                .fechaHora(fechaHora)
                .estado(EstadoCita.PROGRAMADA)
                .build();
    }
}
