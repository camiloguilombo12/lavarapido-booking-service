package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Datos del negocio")
class EstablishmentTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static Establishment with(String legalName, String phone, String email, LocalDate foundedOn) {
        return new Establishment(legalName, "Express Car Wash", "901.482.930-1", null, foundedOn, null, null,
                "Calle 127 #19A-48", phone, email, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String codeOf(Establishment establishment) {
        return assertThrows(InvalidValueException.class, () -> establishment.requireValid(TODAY)).code();
    }

    @Test
    @DisplayName("acepta datos completos y deja vacíos los opcionales")
    void validData() {
        Establishment data = with("Express Car Wash S.A.S.", "+57 312 490 8821", "contacto@expresscarwash.co",
                LocalDate.of(2019, 3, 15));
        assertSame(data, data.requireValid(TODAY));
        Establishment minimal = with("Express Car Wash S.A.S.", null, null, null);
        assertSame(minimal, minimal.requireValid(TODAY));
    }

    @Test
    @DisplayName("rechaza razón social vacía, teléfono corto, correo inválido y fecha futura")
    void invalidData() {
        assertEquals("ESTABLISHMENT_REQUIRED_FIELD", codeOf(with(" ", null, null, null)));
        assertEquals("ESTABLISHMENT_INVALID_PHONE", codeOf(with("Express", "312 49", null, null)));
        assertEquals("ESTABLISHMENT_INVALID_EMAIL", codeOf(with("Express", null, "contacto@", null)));
        assertEquals("ESTABLISHMENT_INVALID_FOUNDATION_DATE", codeOf(with("Express", null, null, TODAY.plusDays(1))));
    }
}
