package com.lavarapido.booking.domain.port.in;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pedido de reserva o de cambio: fecha y hora en la hora local del lavadero. vehicleId se ignora
 * al reprogramar (el vehiculo de una reserva no cambia).
 *
 * paymentMode llega como texto ("DIGITAL" | "CASH") para que el dominio responda
 * INVALID_PAYMENT_MODE si falta o no es valido. Al reprogramar se ignora: el modo no cambia.
 */
public record BookingRequest(Long vehicleId, List<Integer> serviceIds, LocalDate date, LocalTime time, String notes,
                             Integer preferredOperatorId, String paymentMode) {

    public BookingRequest(Long vehicleId, List<Integer> serviceIds, LocalDate date, LocalTime time, String notes,
                          Integer preferredOperatorId) {
        this(vehicleId, serviceIds, date, time, notes, preferredOperatorId, null);
    }

    /** Sin operario preferido: operations-service asigna al disponible con menos servicios (RF-008). */
    public BookingRequest(Long vehicleId, List<Integer> serviceIds, LocalDate date, LocalTime time, String notes) {
        this(vehicleId, serviceIds, date, time, notes, null);
    }
}
