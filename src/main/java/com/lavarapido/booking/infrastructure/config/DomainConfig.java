package com.lavarapido.booking.infrastructure.config;

import com.lavarapido.booking.application.usecase.BookingSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/** El reloj y los parametros de la agenda entran por el contexto para que las pruebas los fijen. */
@Configuration
class DomainConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BookingSettings bookingSettings(@Value("${app.booking.zone:America/Bogota}") String zone,
                                    @Value("${app.booking.slot-minutes:30}") int slotMinutes,
                                    @Value("${app.booking.max-days-ahead:60}") int maxDaysAhead,
                                    @Value("${app.booking.alternatives:5}") int alternatives,
                                    @Value("${app.booking.expiry-grace-minutes:60}") int expiryGraceMinutes,
                                    @Value("${app.booking.cash-active-limit:1}") int cashActiveLimit,
                                    @Value("${app.booking.payment-window:30m}") Duration paymentWindow) {
        if (slotMinutes < 5 || maxDaysAhead < 1 || alternatives < 1 || expiryGraceMinutes < 0
                || cashActiveLimit < 1 || paymentWindow.isNegative() || paymentWindow.isZero()) {
            throw new IllegalStateException("app.booking.* has invalid values");
        }
        return new BookingSettings(ZoneId.of(zone), slotMinutes, maxDaysAhead, alternatives,
                Duration.ofMinutes(expiryGraceMinutes), cashActiveLimit, paymentWindow);
    }
}
