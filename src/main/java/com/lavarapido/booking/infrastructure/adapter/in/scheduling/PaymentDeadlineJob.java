package com.lavarapido.booking.infrastructure.adapter.in.scheduling;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.port.in.BookingUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * D: cada BOOKING_PAYMENT_EXPIRY_INTERVAL (60 s) cancela con PAYMENT_TIMEOUT las reservas DIGITAL
 * que llegaron a su plazo sin comprobante. Cada una va en su propia transaccion
 * (BookingService.cancelForPaymentTimeout), por el mismo camino de una cancelacion normal: sale
 * BookingCancelled y operations y notification reaccionan como siempre.
 *
 * Si otra peticion cambio la reserva al mismo tiempo (row_version) o la agenda estaba ocupada, esa se
 * salta y se vuelve a mirar en la siguiente vuelta. Se apaga con BOOKING_PAYMENT_EXPIRY_ENABLED=false.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.booking.payment-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentDeadlineJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentDeadlineJob.class);

    private final BookingUseCase bookings;

    public PaymentDeadlineJob(BookingUseCase bookings) {
        this.bookings = bookings;
    }

    @Scheduled(initialDelayString = "${app.booking.payment-expiry.interval:60s}",
            fixedDelayString = "${app.booking.payment-expiry.interval:60s}")
    public void cancelOverdue() {
        List<Long> overdue;
        try {
            overdue = bookings.paymentOverdueBookings();
        } catch (RuntimeException e) {
            // base caida: se intenta en la siguiente vuelta
            log.warn("Could not look for bookings with an overdue payment: {}", e.getMessage());
            return;
        }
        int cancelled = 0;
        for (Long bookingId : overdue) {
            try {
                if (bookings.cancelForPaymentTimeout(bookingId)) {
                    cancelled++;
                    log.info("Booking {} cancelled: the payment was not reported before its deadline", bookingId);
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("Booking {} changed while its payment deadline was checked, retrying next round", bookingId);
            } catch (ConflictException e) {
                // SCHEDULE_BUSY (la agenda tardo en soltarse) o la reserva ya no se puede cancelar
                log.info("Booking {} skipped this round: {}", bookingId, e.code());
            } catch (RuntimeException e) {
                log.warn("Could not cancel booking {} for payment timeout: {}", bookingId, e.getMessage());
            }
        }
        if (cancelled > 0) {
            log.info("Cancelled {} booking(s) for payment timeout", cancelled);
        }
    }
}
