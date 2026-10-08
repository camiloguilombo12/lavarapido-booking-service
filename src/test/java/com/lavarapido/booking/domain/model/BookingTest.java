package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Booking (reglas de la reserva)")
class BookingTest {

    private static final Instant NOW = Instant.parse("2026-09-30T15:00:00Z");
    private static final Instant START = Instant.parse("2026-10-01T14:00:00Z");
    private static final Instant END = Instant.parse("2026-10-01T15:15:00Z");
    private static final BookingLine PREMIUM =
            new BookingLine(10L, 2, "PREMIUM", "Premium", new BigDecimal("35000"), (short) 75, (short) 1, 30);
    private static final BookingLine BASIC =
            new BookingLine(11L, 1, "BASIC", "Basico", new BigDecimal("20000"), (short) 45, (short) 1, 10);
    private static final CancellationReason CUSTOMER =
            new CancellationReason((short) 1, "CUSTOMER_REQUEST", "El cliente la cancelo", false);

    private static Booking confirmed() {
        return Booking.confirmNew(5L, List.of(PREMIUM, BASIC), START, END, (short) 1, 7L, "  sin cera  ", NOW);
    }

    @Test
    @DisplayName("una reserva nueva queda confirmada, con total y duracion calculados")
    void newBookingIsConfirmed() {
        Booking booking = confirmed();

        assertEquals(BookingStatus.CONFIRMED, booking.status());
        assertEquals(new BigDecimal("55000"), booking.total());
        assertEquals(120, booking.durationMinutes());
        assertEquals("sin cera", booking.notes());
    }

    @Test
    @DisplayName("INV-BOOK-003/004: sin servicios o con rango invertido no se crea")
    void requiresLinesAndValidRange() {
        assertThrows(InvalidValueException.class,
                () -> Booking.confirmNew(5L, List.of(), START, END, (short) 1, 7L, null, NOW));
        assertThrows(InvalidValueException.class,
                () -> Booking.confirmNew(5L, List.of(PREMIUM), END, START, (short) 1, 7L, null, NOW));
        assertThrows(InvalidValueException.class,
                () -> Booking.confirmNew(5L, List.of(PREMIUM, PREMIUM), START, END, (short) 1, 7L, null, NOW));
    }

    @Test
    @DisplayName("el codigo sale del id")
    void codeComesFromTheId() {
        Booking booking = confirmed();
        booking.assignId(42L);
        assertEquals("RES-000042", booking.code());
    }

    @Test
    @DisplayName("RF-007: no se cancela ni se cambia despues de empezar")
    void cannotChangeAfterStarting() {
        Booking booking = confirmed();
        booking.moveTo(BookingStatus.IN_PROGRESS);

        assertThrows(ConflictException.class, () -> booking.cancel(CUSTOMER));
        assertThrows(ConflictException.class,
                () -> booking.reschedule(List.of(BASIC), START, END, (short) 2, null));
    }

    @Test
    @DisplayName("el cliente puede cambiarla solo antes de la hora de inicio")
    void customerWindow() {
        Booking booking = confirmed();
        assertTrue(booking.changeableByCustomer(NOW));
        assertFalse(booking.changeableByCustomer(START));
    }

    @Test
    @DisplayName("cancelar guarda el motivo y deja la reserva final")
    void cancelKeepsTheReason() {
        Booking booking = confirmed();
        booking.cancel(CUSTOMER);

        assertEquals(BookingStatus.CANCELLED, booking.status());
        assertEquals("CUSTOMER_REQUEST", booking.cancellationReason().code());
        assertFalse(booking.status().occupiesBay());
    }

    @Test
    @DisplayName("transiciones del admin: CONFIRMED -> IN_PROGRESS -> COMPLETED, y no al reves")
    void statusTransitions() {
        Booking booking = confirmed();
        booking.moveTo(BookingStatus.IN_PROGRESS);
        booking.moveTo(BookingStatus.COMPLETED);

        assertThrows(ConflictException.class, () -> booking.moveTo(BookingStatus.IN_PROGRESS));
        assertThrows(ConflictException.class, () -> confirmed().moveTo(BookingStatus.COMPLETED));
    }

    @Test
    @DisplayName("reprogramar cambia franja, bahia y servicios")
    void rescheduleChangesEverything() {
        Booking booking = confirmed();
        Instant newStart = START.plusSeconds(3600 * 4);
        booking.reschedule(List.of(BASIC), newStart, newStart.plusSeconds(45 * 60), (short) 3, "otra nota");

        assertEquals(newStart, booking.scheduledStart());
        assertEquals((short) 3, booking.serviceBayId());
        assertEquals(new BigDecimal("20000"), booking.total());
    }

    @Test
    @DisplayName("un horario con la pausa fuera del dia no es valido")
    void businessHourValidatesBreak() {
        assertThrows(InvalidValueException.class, () -> new BusinessHour((short) 1, true,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(17, 0),
                java.time.LocalTime.of(7, 0), java.time.LocalTime.of(9, 0)));
        assertThrows(InvalidValueException.class, () -> new BusinessHour((short) 8, true,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(17, 0), null, null));
    }
}
