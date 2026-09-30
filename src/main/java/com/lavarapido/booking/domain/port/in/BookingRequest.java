package com.lavarapido.booking.domain.port.in;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pedido de reserva o de cambio: fecha y hora en la hora local del lavadero. vehicleId se ignora
 * al reprogramar (el vehiculo de una reserva no cambia).
 */
public record BookingRequest(Long vehicleId, List<Integer> serviceIds, LocalDate date, LocalTime time, String notes) {
}
