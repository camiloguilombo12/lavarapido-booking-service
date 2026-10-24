package com.lavarapido.booking.infrastructure.adapter.out.messaging;

import com.lavarapido.booking.domain.event.BookingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Payload de los eventos de reserva")
class RabbitDomainEventPublisherTest {

    private static final Instant START = Instant.parse("2026-10-01T14:00:00Z");

    @Test
    @DisplayName("A-20 / OPS-10: BookingCancelled lleva reason (texto) y reasonCode (código)")
    void cancelledPayloadHasTheReasonCode() {
        BookingEvent cancelled = new BookingEvent(BookingEvent.Type.CANCELLED, 9L, "RES-000009", 7L, 5L, START,
                (short) 1, "Problema del lavadero", START, null, null, "SHOP_ISSUE");

        Map<String, Object> payload = RabbitDomainEventPublisher.payloadOf(cancelled);

        assertEquals("Problema del lavadero", payload.get("reason"));
        assertEquals("SHOP_ISSUE", payload.get("reasonCode"));
    }

    @Test
    @DisplayName("los eventos que no son cancelaciones no traen reasonCode")
    void otherEventsHaveNoReasonCode() {
        BookingEvent confirmed = new BookingEvent(BookingEvent.Type.CONFIRMED, 9L, "RES-000009", 7L, 5L, START,
                (short) 1, null, START);

        assertFalse(RabbitDomainEventPublisher.payloadOf(confirmed).containsKey("reasonCode"));
    }
}
