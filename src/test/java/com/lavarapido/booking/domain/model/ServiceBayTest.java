package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Bahía")
class ServiceBayTest {

    @Test
    @DisplayName("una bahía nueva nace activa si no se dice otra cosa")
    void newBayDefaultsToActive() {
        assertEquals(BayStatus.ACTIVE, ServiceBay.requireInitialStatus(null));
    }

    @Test
    @DisplayName("una bahía nueva puede nacer activa o inactiva")
    void newBayCanBeActiveOrInactive() {
        assertEquals(BayStatus.ACTIVE, ServiceBay.requireInitialStatus(BayStatus.ACTIVE));
        assertEquals(BayStatus.INACTIVE, ServiceBay.requireInitialStatus(BayStatus.INACTIVE));
    }

    @Test
    @DisplayName("una bahía nueva no puede nacer en mantenimiento")
    void newBayCannotStartInMaintenance() {
        InvalidValueException error = assertThrows(InvalidValueException.class,
                () -> ServiceBay.requireInitialStatus(BayStatus.MAINTENANCE));
        assertEquals("BAY_NEW_IN_MAINTENANCE", error.code());
    }
}
