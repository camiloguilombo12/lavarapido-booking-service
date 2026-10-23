package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.event.BookingEvent;

/** Publica eventos de reserva despues de que la transaccion se confirma (ADR-004). */
public interface DomainEventPublisher {

    void publish(BookingEvent event);
}
