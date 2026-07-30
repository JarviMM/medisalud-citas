package com.medisalud.agenda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.medisalud.agenda.domain.Medico;
import com.medisalud.agenda.repository.MedicoRepository;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Carga inicial de los tres medicos de ejemplo.
 *
 * <p>Se comprueban los datos exactos, incluidas las tildes, porque son parte del entregable:
 * si el fichero fuente dejara de leerse como UTF-8, este test lo detecta antes que una
 * inspeccion visual de la respuesta.</p>
 */
@ExtendWith(MockitoExtension.class)
class CargaInicialMedicosTest {

    @Mock private MedicoRepository medicoRepository;

    @Captor private ArgumentCaptor<List<Medico>> capturaDeMedicos;

    @InjectMocks private CargaInicialMedicos cargaInicial;

    @Test
    @DisplayName("Con la base vacía precarga los tres médicos del enunciado")
    void precargaLosTresMedicos() {
        given(medicoRepository.count()).willReturn(0L);

        cargaInicial.run();

        verify(medicoRepository).saveAll(capturaDeMedicos.capture());

        assertThat(capturaDeMedicos.getValue())
                .hasSize(3)
                .extracting(Medico::getNombreCompleto, Medico::getEspecialidad,
                        Medico::getTelefono, Medico::getEmail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Dra. María González", "Cardiología",
                                "555-1001", "maria.gonzalez@medisalud.com"),
                        org.assertj.core.groups.Tuple.tuple("Dr. Carlos Ruiz", "Pediatría",
                                "555-1002", "carlos.ruiz@medisalud.com"),
                        org.assertj.core.groups.Tuple.tuple("Dra. Ana López", "Dermatología",
                                "555-1003", "ana.lopez@medisalud.com"));
    }

    @Test
    @DisplayName("Es idempotente: si ya hay médicos no vuelve a insertarlos")
    void noDuplicaSiYaHayDatos() {
        given(medicoRepository.count()).willReturn(3L);

        cargaInicial.run();

        verify(medicoRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Los médicos se crean sin identificador: lo asigna la base de datos")
    void sinIdentificadorPrefijado() {
        given(medicoRepository.count()).willReturn(0L);

        cargaInicial.run();

        verify(medicoRepository).saveAll(capturaDeMedicos.capture());
        assertThat(capturaDeMedicos.getValue())
                .asInstanceOf(InstanceOfAssertFactories.list(Medico.class))
                .allSatisfy(medico -> assertThat(medico.getId()).isNull());
    }
}
