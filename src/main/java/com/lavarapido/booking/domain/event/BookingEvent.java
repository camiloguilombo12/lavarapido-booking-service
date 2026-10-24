package com.lavarapido.booking.domain.event;

import java.time.Instant;

/**
 * Evento de reserva que escuchan notification-service (ADR-011) y, mas adelante, operations y
 * payment. Lleva el user_id del cliente porque notification no conoce los vehiculos.
 *
 * type es el nombre del evento (BookingConfirmed...) y routingKey la llave en carwash.events
 * (booking.confirmed...), como en cross-cutting.md §7.
 *
 * En BookingCancelled, reason es el nombre del motivo (texto en español, para mostrar) y reasonCode
 * su codigo estable (CUSTOMER_REQUEST, SHOP_ISSUE...), para decidir sin depender del texto (A-20, OPS-10).
 */
public record BookingEvent(Type type, long bookingId, String bookingCode, Long customerUserId,
                           long customerVehicleId, Instant scheduledStart, Short serviceBayId,
                           String reason, Instant occurredAt, Integer preferredOperatorId, String status,
                           String reasonCode) {

    /** Sin codigo de motivo (todo lo que no es una cancelacion). */
    public BookingEvent(Type type, long bookingId, String bookingCode, Long customerUserId, long customerVehicleId,
                        Instant scheduledStart, Short serviceBayId, String reason, Instant occurredAt,
                        Integer preferredOperatorId, String status) {
        this(type, bookingId, bookingCode, customerUserId, customerVehicleId, scheduledStart, serviceBayId, reason,
                occurredAt, preferredOperatorId, status, null);
    }

    /** Con operario preferido (al crear la reserva). */
    public BookingEvent(Type type, long bookingId, String bookingCode, Long customerUserId, long customerVehicleId,
                        Instant scheduledStart, Short serviceBayId, String reason, Instant occurredAt,
                        Integer preferredOperatorId) {
        this(type, bookingId, bookingCode, customerUserId, customerVehicleId, scheduledStart, serviceBayId, reason,
                occurredAt, preferredOperatorId, null);
    }

    /** Sin operario preferido (reprogramación, cancelación). */
    public BookingEvent(Type type, long bookingId, String bookingCode, Long customerUserId, long customerVehicleId,
                        Instant scheduledStart, Short serviceBayId, String reason, Instant occurredAt) {
        this(type, bookingId, bookingCode, customerUserId, customerVehicleId, scheduledStart, serviceBayId, reason,
                occurredAt, null, null);
    }

    public enum Type {
        CONFIRMED("BookingConfirmed", "booking.confirmed"),
        CANCELLED("BookingCancelled", "booking.cancelled"),
        // A-11: el administrador movió la reserva a en curso, completada o no asistió
        STATUS_CHANGED("BookingStatusChanged", "booking.status_changed");

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
