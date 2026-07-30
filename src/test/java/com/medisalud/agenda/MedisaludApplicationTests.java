package com.medisalud.agenda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de humo: si el contexto de Spring no levanta, ningun otro test es fiable.
 */
@SpringBootTest
@ActiveProfiles("test")
class MedisaludApplicationTests {

    @Test
    @DisplayName("El contexto de la aplicacion se carga correctamente")
    void contextLoads() {
        // El propio arranque del contexto es la asercion.
    }
}
