package com.medisalud.agenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del MVP de agendamiento de citas medicas de MediSalud.
 *
 * <p>El perfil activo por defecto es {@code dev} (ver {@code application.yml}), de modo
 * que la aplicacion arranca con un solo comando: {@code mvn spring-boot:run}.</p>
 */
@SpringBootApplication
public class MedisaludApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedisaludApplication.class, args);
    }
}
