package com.lavarapido.booking.application.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Catalogo")
class CatalogServiceTest {

    @Test
    @DisplayName("el codigo se arma con el nombre, sin tildes y en mayusculas")
    void codeFromName() {
        assertEquals("LAVADO_DE_MOTOR", CatalogService.codeFromName("Lavado de motor"));
        assertEquals("DESINFECCION_TAPICERIA", CatalogService.codeFromName(" Desinfección + tapicería "));
    }
}
