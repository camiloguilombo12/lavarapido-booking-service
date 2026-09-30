package com.lavarapido.booking.domain.event;

import java.time.Instant;

/**
 * Evento de reserva que escuchan notification-service (ADR-011) y, mas adelante, operations y
 * payment. Lleva el user_id del cliente porque notification no conoce los vehiculos.
 *
 * type es el nombre del evento (BookingConfirmed...) y routingKey la llave en carwash.events
 * (booking.confirmed...), como en cross-cutting.md §7.
 */
public record BookingEvent(Type type, long bookingId, String bookingCode, Long customerUserId,
                           long customerVehicleId, Instant scheduledStart, Short serviceBayId,
                           String reason, Instant occurredAt) {

    public enum Type {
        CONFIRMED("BookingConfirmed", "booking.confirmed"),
        CANCELLED("BookingCancelled", "booking.cancelled");

        private final String eventName;
        private final String routingKey;

        Type(String eventName, String routingKey) {
            this.eventName = eventName;
            this.routingKey = routingKey;
        }

        public String eventName() {
            return eventName;
        }

        public String routingKey() {
            return routingKey;
        }
    }
}
