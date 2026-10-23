package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
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

    @Test
    @DisplayName("vence cuando pasa la hora de fin mas el margen y nunca se inicio")
    void expiresAfterTheEndPlusTheGrace() {
        Booking booking = confirmed();
        Duration hour = Duration.ofHours(1);

        assertFalse(booking.isExpired(END.plus(Duration.ofMinutes(59)), hour));
        assertTrue(booking.isExpired(END.plus(hour), hour));
        booking.moveTo(BookingStatus.IN_PROGRESS);
        assertFalse(booking.isExpired(END.plus(Duration.ofDays(1)), hour));
    }

    @Test
    @DisplayName("al vencer, solo la programada se cancela sola, con el motivo del sistema")
    void onlyAScheduledBookingExpiresByItself() {
        CancellationReason expired = new CancellationReason((short) 7, "EXPIRED", "Vencio sin confirmarse", false);
        Booking scheduled = Booking.restore(8L, 5L, null, START, END, BookingStatus.SCHEDULED, 7L, null, 0,
                BigDecimal.ZERO, null, List.of(PREMIUM), NOW);

        scheduled.expire(expired);

        assertEquals(BookingStatus.CANCELLED, scheduled.status());
        assertEquals("EXPIRED", scheduled.cancellationReason().code());
        // una confirmada pudo atenderse sin registrarse: no se cierra sola
        assertThrows(ConflictException.class, () -> confirmed().expire(expired));
    }

    @Test
    @DisplayName("una confirmada sin operario queda NO_SHOW solo despues de la hora de fin mas el margen")
    void anUnattendedBookingIsClosedOnlyOnceExpired() {
        Booking booking = confirmed();
        Duration hour = Duration.ofHours(1);

        ConflictException early = assertThrows(ConflictException.class,
                () -> booking.markUnattended(END.plus(Duration.ofMinutes(30)), hour));
        assertEquals("BOOKING_NOT_EXPIRED", early.code());

        booking.markUnattended(END.plus(hour), hour);
        assertEquals(BookingStatus.NO_SHOW, booking.status());
        assertThrows(ConflictException.class, () -> booking.markUnattended(END.plus(hour), hour));
    }
}
