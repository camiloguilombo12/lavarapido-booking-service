package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Tarifa de un servicio")
class PriceDefinitionTest {

    @Test
    @DisplayName("BIZ-04: un precio en 0 o negativo es INVALID_PRICE")
    void priceMustBeGreaterThanZero() {
        for (String price : new String[] {"0", "0.00", "-1"}) {
            InvalidValueException error = assertThrows(InvalidValueException.class,
                    () -> new PriceDefinition((short) 1, new BigDecimal(price), (short) 45));
            assertEquals("INVALID_PRICE", error.code());
        }
    }

    @Test
    @DisplayName("BIZ-04: el precio más pequeño que sirve es un centavo")
    void smallestPriceIsAccepted() {
        assertEquals(new BigDecimal("0.01"), new PriceDefinition((short) 1, new BigDecimal("0.01"), (short) 45).price());
    }
}
