package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.model.VehicleSnapshot;

import java.time.LocalDateTime;

/**
 * La reserva lista para mostrar: con el vehiculo (de customer-service), la bahia, las horas en la
 * zona del lavadero y si quien la ve todavia la puede cambiar. Asi el frontend no calcula nada.
 * vehicle es null si customer-service no la encontro (por ejemplo, un vehiculo ya borrado).
 */
public record BookingView(Booking booking, VehicleSnapshot vehicle, ServiceBay bay,
                          LocalDateTime localStart, LocalDateTime localEnd, boolean changeable) {
}
