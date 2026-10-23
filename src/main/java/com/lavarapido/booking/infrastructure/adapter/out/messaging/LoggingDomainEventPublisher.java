package com.lavarapido.booking.infrastructure.adapter.out.messaging;

import com.lavarapido.booking.domain.event.BookingEvent;
import com.lavarapido.booking.domain.port.out.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Con MESSAGING_ENABLED=false los eventos solo quedan en el log (desarrollo sin RabbitMQ). */
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "false", matchIfMissing = true)
class LoggingDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

    @Override
    public void publish(BookingEvent event) {
        log.info("Domain event {} for booking {} (messaging disabled, not published)",
                event.type().eventName(), event.bookingId());
    }
}
