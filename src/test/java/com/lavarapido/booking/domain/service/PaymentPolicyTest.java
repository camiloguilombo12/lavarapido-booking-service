package com.lavarapido.booking.domain.service;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.model.PaymentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PaymentPolicy (efectivo con límite y plazo del pago digital)")
class PaymentPolicyTest {

    private static final Instant CREATED = Instant.parse("2026-10-26T15:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(30);

    @Test
    @DisplayName("D: el plazo es la creación + la ventana si la reserva empieza después")
    void deadlineIsTheWindowWhenTheBookingIsLater() {
        Instant start = Instant.parse("2026-10-27T14:00:00Z");

        assertEquals(Instant.parse("2026-10-26T15:30:00Z"),
                PaymentPolicy.deadlineFor(PaymentMode.DIGITAL, CREATED, start, WINDOW));
    }

    @Test
    @DisplayName("D: si la reserva empieza antes de que se acabe la ventana, el plazo es la hora de inicio (el mínimo)")
    void deadlineIsTheStartWhenItComesFirst() {
        Instant start = Instant.parse("2026-10-26T15:20:00Z");

        assertEquals(start, PaymentPolicy.deadlineFor(PaymentMode.DIGITAL, CREATED, start, WINDOW));
        // justo en el borde los dos valen lo mismo
        Instant edge = CREATED.plus(WINDOW);
        assertEquals(edge, PaymentPolicy.deadlineFor(PaymentMode.DIGITAL, CREATED, edge, WINDOW));
    }

    @Test
    @DisplayName("D: el efectivo y una reserva sin modo no tienen plazo")
    void onlyDigitalHasADeadline() {
        Instant start = Instant.parse("2026-10-27T14:00:00Z");

        assertNull(PaymentPolicy.deadlineFor(PaymentMode.CASH, CREATED, start, WINDOW));
        assertNull(PaymentPolicy.deadlineFor(null, CREATED, start, WINDOW));
    }

    @Test
    @DisplayName("B: por debajo del límite pasa; al llegar al límite es 409 CASH_BOOKING_LIMIT")
    void cashLimit() {
        assertDoesNotThrow(() -> PaymentPolicy.requireCashAllowed(0, 1));
        assertDoesNotThrow(() -> PaymentPolicy.requireCashAllowed(1, 2));

        ConflictException atLimit = assertThrows(ConflictException.class, () -> PaymentPolicy.requireCashAllowed(1, 1));
        assertEquals("CASH_BOOKING_LIMIT", atLimit.code());
        assertThrows(ConflictException.class, () -> PaymentPolicy.requireCashAllowed(3, 2));
    }

    @Test
    @DisplayName("el modo de pago: DIGITAL o CASH (sin importar mayúsculas); lo demás es INVALID_PAYMENT_MODE")
    void paymentModeParsing() {
        assertEquals(PaymentMode.DIGITAL, PaymentMode.of("DIGITAL"));
        assertEquals(PaymentMode.CASH, PaymentMode.of(" cash "));
        assertEquals("INVALID_PAYMENT_MODE", assertThrows(InvalidValueException.class, () -> PaymentMode.of(null)).code());
        assertEquals("INVALID_PAYMENT_MODE", assertThrows(InvalidValueException.class, () -> PaymentMode.of(" ")).code());
        assertEquals("INVALID_PAYMENT_MODE", assertThrows(InvalidValueException.class, () -> PaymentMode.of("CARD")).code());

        // para el admin es opcional, pero un valor que no existe sigue siendo error
        assertNull(PaymentMode.ofOptional(null));
        assertEquals(PaymentMode.CASH, PaymentMode.ofOptional("CASH"));
        assertThrows(InvalidValueException.class, () -> PaymentMode.ofOptional("BITCOIN"));

        assertNull(PaymentMode.fromColumn(null));
        assertEquals(PaymentMode.DIGITAL, PaymentMode.fromColumn("DIGITAL"));
    }
}
