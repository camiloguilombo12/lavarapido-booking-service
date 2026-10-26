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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Booking (reglas de la reserva)")
class BookingTest {

    private static final Instant NOW = Instant.parse("2026-09-30T15:00:00Z");
    private static final Instant START = Instant.parse("2026-10-01T14:00:00Z");
    private static final Instant END = Instant.parse("2026-10-01T15:15:00Z");
    private static final BookingLine PREMIUM =
            new BookingLine(10L, 2, "PREMIUM", "Premium", new BigDecimal("35000"), (short) 75, (short) 1, 30, (short) 3);
    private static final BookingLine BASIC =
            new BookingLine(11L, 1, "BASIC", "Basico", new BigDecimal("20000"), (short) 45, (short) 1, 10, (short) 3);
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
    @DisplayName("E-01, RN-EXEC-01 / INV-EXEC-004: el operario inicia desde 15 minutos antes, no antes")
    void operatorStartsFromFifteenMinutesBefore() {
        Booking early = confirmed();
        ConflictException tooEarly = assertThrows(ConflictException.class,
                () -> early.startByOperator(START.minus(Duration.ofMinutes(16))));
        assertEquals("SERVICE_TOO_EARLY", tooEarly.code());
        assertEquals(BookingStatus.CONFIRMED, early.status());

        Booking onTime = confirmed();
        onTime.startByOperator(START.minus(Duration.ofMinutes(15)));
        assertEquals(BookingStatus.IN_PROGRESS, onTime.status());
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
    @DisplayName("B-07: una reserva nace y se reprograma CONFIRMED; una SCHEDULED vieja pasa a CONFIRMED al moverla")
    void bookingsNeverStayScheduled() {
        Booking booking = confirmed();
        Instant newStart = START.plusSeconds(3600 * 2);
        booking.reschedule(List.of(PREMIUM), newStart, newStart.plusSeconds(75 * 60), (short) 2, null);
        Booking legacy = Booking.restore(8L, 5L, null, START, END, BookingStatus.SCHEDULED, 7L, null, 0,
                BigDecimal.ZERO, null, List.of(PREMIUM), NOW);
        legacy.reschedule(List.of(PREMIUM), newStart, newStart.plusSeconds(75 * 60), (short) 2, null);

        // por eso no hay tarea que cierre programadas vencidas: las confirmadas sin operario las cierra operations
        assertEquals(BookingStatus.CONFIRMED, booking.status());
        assertEquals(BookingStatus.CONFIRMED, legacy.status());
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

    private static Booking digital(BookingStatus status, Instant deadline, Instant reportedAt) {
        return Booking.restore(9L, 5L, (short) 1, START, END, status, 7L, null, 0, BigDecimal.ZERO, null,
                List.of(PREMIUM), NOW, PaymentMode.DIGITAL, deadline, reportedAt);
    }

    @Test
    @DisplayName("contrato de pago: una reserva nueva guarda el modo y el plazo, y nace sin reporte")
    void newBookingKeepsThePaymentTerms() {
        Instant deadline = NOW.plus(Duration.ofMinutes(30));
        Booking booking = Booking.confirmNew(5L, List.of(PREMIUM), START, END, (short) 1, 7L, null, NOW,
                PaymentMode.DIGITAL, deadline);

        assertEquals(PaymentMode.DIGITAL, booking.paymentMode());
        assertEquals(deadline, booking.paymentDeadline());
        assertFalse(booking.paymentReported());
        // las de antes del cambio no tienen modo y nunca vencen
        assertNull(confirmed().paymentMode());
        assertFalse(confirmed().paymentOverdue(START));
    }

    @Test
    @DisplayName("D: vence solo la DIGITAL sin reporte, con el plazo cumplido y que aún se puede cancelar")
    void paymentOverdue() {
        Instant deadline = NOW.plus(Duration.ofMinutes(30));

        assertFalse(digital(BookingStatus.CONFIRMED, deadline, null).paymentOverdue(deadline.minusSeconds(1)));
        assertTrue(digital(BookingStatus.CONFIRMED, deadline, null).paymentOverdue(deadline));
        assertTrue(digital(BookingStatus.SCHEDULED, deadline, null).paymentOverdue(deadline.plusSeconds(60)));
        // con comprobante, ya empezada o ya cancelada no vence
        assertFalse(digital(BookingStatus.CONFIRMED, deadline, NOW).paymentOverdue(deadline.plusSeconds(60)));
        assertFalse(digital(BookingStatus.IN_PROGRESS, deadline, null).paymentOverdue(deadline.plusSeconds(60)));
        assertFalse(digital(BookingStatus.CANCELLED, deadline, null).paymentOverdue(deadline.plusSeconds(60)));
        // sin plazo (la creó el admin) o en efectivo nunca vence
        assertFalse(digital(BookingStatus.CONFIRMED, null, null).paymentOverdue(END));
        Booking cash = Booking.confirmNew(5L, List.of(PREMIUM), START, END, (short) 1, 7L, null, NOW,
                PaymentMode.CASH, null);
        assertFalse(cash.paymentOverdue(END));
    }

    @Test
    @DisplayName("el reporte del pago se guarda una sola vez y no toca una cancelada")
    void paymentReportedIsIdempotent() {
        Booking booking = digital(BookingStatus.CONFIRMED, NOW.plusSeconds(1800), null);
        Instant first = NOW.plusSeconds(60);

        assertTrue(booking.markPaymentReported(first));
        assertFalse(booking.markPaymentReported(first.plusSeconds(60)));
        assertEquals(first, booking.paymentReportedAt());
        assertTrue(booking.paymentReported());

        Booking cancelled = digital(BookingStatus.CANCELLED, NOW.plusSeconds(1800), null);
        assertFalse(cancelled.markPaymentReported(first));
        assertFalse(cancelled.paymentReported());
    }

    @Test
    @DisplayName("regla 7: reprogramar no cambia ni el modo ni el plazo")
    void rescheduleKeepsThePaymentTerms() {
        Instant deadline = NOW.plus(Duration.ofMinutes(30));
        Booking booking = digital(BookingStatus.CONFIRMED, deadline, null);

        booking.reschedule(List.of(PREMIUM), START.plusSeconds(3600), END.plusSeconds(3600), (short) 2, null);

        assertEquals(PaymentMode.DIGITAL, booking.paymentMode());
        assertEquals(deadline, booking.paymentDeadline());
    }
}
