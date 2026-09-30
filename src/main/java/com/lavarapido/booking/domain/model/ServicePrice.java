package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tarifa de un servicio para un tipo de vehiculo. Es inmutable: un cambio de precio cierra esta
 * fila y crea otra, asi una reserva vieja conserva el precio con el que se hizo (INV-BOOK-008).
 */
public record ServicePrice(long id, int serviceId, short vehicleTypeId, BigDecimal price,
                           short estimatedMinutes, LocalDate validFrom, LocalDate validTo) {
}
