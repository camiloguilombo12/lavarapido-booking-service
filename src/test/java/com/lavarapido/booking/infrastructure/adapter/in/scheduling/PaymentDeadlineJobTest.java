package com.lavarapido.booking.infrastructure.adapter.in.scheduling;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.port.in.BookingUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PaymentDeadlineJob (cancela las reservas DIGITAL vencidas sin comprobante)")
class PaymentDeadlineJobTest {

    private final BookingUseCase bookings = mock(BookingUseCase.class);
    private final PaymentDeadlineJob job = new PaymentDeadlineJob(bookings);

    @Test
    @DisplayName("cancela cada reserva vencida, una por una")
    void cancelsEveryOverdueBooking() {
        given(bookings.paymentOverdueBookings()).willReturn(List.of(10L, 11L));
        given(bookings.cancelForPaymentTimeout(10L)).willReturn(true);
        // la 11 recibió el comprobante entre la consulta y la cancelación: el caso de uso no la toca
        given(bookings.cancelForPaymentTimeout(11L)).willReturn(false);

        job.cancelOverdue();

        verify(bookings).cancelForPaymentTimeout(10L);
        verify(bookings).cancelForPaymentTimeout(11L);
    }

    @Test
    @DisplayName("sin reservas vencidas no cancela nada")
    void nothingOverdue() {
        given(bookings.paymentOverdueBookings()).willReturn(List.of());

        job.cancelOverdue();

        verify(bookings, never()).cancelForPaymentTimeout(anyLong());
    }

    @Test
    @DisplayName("un choque de row_version o la agenda ocupada se salta y sigue con las demás (se reintenta en la siguiente vuelta)")
    void conflictsAreSkipped() {
        given(bookings.paymentOverdueBookings()).willReturn(List.of(10L, 11L, 12L));
        given(bookings.cancelForPaymentTimeout(10L))
                .willThrow(new ObjectOptimisticLockingFailureException("BookingJpaEntity", 10L));
        given(bookings.cancelForPaymentTimeout(11L)).willThrow(new ConflictException("SCHEDULE_BUSY", "busy"));
        given(bookings.cancelForPaymentTimeout(12L)).willReturn(true);

        assertDoesNotThrow(job::cancelOverdue);

        verify(bookings).cancelForPaymentTimeout(12L);
    }

    @Test
    @DisplayName("si la base no responde al buscar, la vuelta termina sin romper el planificador")
    void databaseDownDoesNotBreakTheScheduler() {
        given(bookings.paymentOverdueBookings()).willThrow(new DataAccessResourceFailureException("db down"));

        assertDoesNotThrow(job::cancelOverdue);
        verify(bookings, never()).cancelForPaymentTimeout(anyLong());
    }
}
