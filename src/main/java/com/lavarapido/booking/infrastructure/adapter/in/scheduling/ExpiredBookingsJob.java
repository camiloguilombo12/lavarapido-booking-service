package com.lavarapido.booking.infrastructure.adapter.in.scheduling;

import com.lavarapido.booking.domain.port.in.BookingUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada pocos minutos cierra las reservas que ya pasaron y nunca se iniciaron (BookingService.closeExpired).
 * Se apaga con app.booking.expiry-check.enabled=false.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.booking.expiry-check.enabled", havingValue = "true", matchIfMissing = true)
class ExpiredBookingsJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredBookingsJob.class);

    private final BookingUseCase bookings;

    ExpiredBookingsJob(BookingUseCase bookings) {
        this.bookings = bookings;
    }

    @Scheduled(initialDelayString = "${app.booking.expiry-check.initial-delay:PT1M}",
            fixedDelayString = "${app.booking.expiry-check.interval:PT5M}")
    void closeExpired() {
        try {
            int closed = bookings.closeExpired();
            if (closed > 0) {
                log.info("Closed {} expired bookings", closed);
            }
        } catch (RuntimeException ex) {
            // si falla (base caída, agenda ocupada) se intenta en la siguiente vuelta
            log.warn("Could not close expired bookings: {}", ex.getMessage());
        }
    }
}
