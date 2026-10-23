package com.lavarapido.booking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationReasonTest {

    private static CancellationReason reason(String code, boolean customerFault) {
        return new CancellationReason((short) 1, code, code, customerFault);
    }

    @Test
    @DisplayName("A-20: el cliente elige motivos de su lado; el admin, cualquiera menos el del sistema")
    void whoCanChooseEachReason() {
        assertTrue(reason("SCHEDULE_CONFLICT", false).selectableBy(false));
        assertTrue(reason("CUSTOMER_REQUEST", false).selectableBy(false));
        assertFalse(reason("LATE_ARRIVAL", true).selectableBy(false));
        assertFalse(reason("SHOP_ISSUE", false).selectableBy(false));

        assertTrue(reason("LATE_ARRIVAL", true).selectableBy(true));
        assertTrue(reason("SHOP_ISSUE", false).selectableBy(true));
        assertFalse(reason("EXPIRED", false).selectableBy(true));
        assertFalse(reason("EXPIRED", false).selectableBy(false));
    }
}
