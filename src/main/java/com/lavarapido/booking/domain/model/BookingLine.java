package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;

/**
 * Una linea de la reserva: apunta a la tarifa (service_price) con la que se cobro. El nombre y el
 * precio salen de esa fila, que nunca cambia (INV-BOOK-008). lineId es el booking_service_id: null
 * mientras la linea no se ha guardado. operations-service lo usa para la ejecucion de cada linea.
 *
 * loyaltyPoints es los puntos de fidelizacion que paga este servicio, leidos de
 * catalog.service.loyalty_points al armar la linea y congelados en booking.booking_service.loyalty_points
 * (A-09, changelog 043): si el admin cambia los puntos del servicio despues, la reserva conserva los suyos.
 *
 * vehicleTypeId es el tipo de vehiculo de esa tarifa (service_price.vehicle_type_id): al reprogramar,
 * la tarifa congelada solo se conserva si el vehiculo sigue siendo de ese tipo (BIZ-06).
 */
public record BookingLine(long servicePriceId, int serviceId, String serviceCode, String serviceName,
                          BigDecimal price, short estimatedMinutes, short quantity, Long lineId, int loyaltyPoints,
                          short vehicleTypeId) {

    /** Linea nueva, todavia sin guardar. */
    public BookingLine(long servicePriceId, int serviceId, String serviceCode, String serviceName,
                       BigDecimal price, short estimatedMinutes, short quantity, int loyaltyPoints,
                       short vehicleTypeId) {
        this(servicePriceId, serviceId, serviceCode, serviceName, price, estimatedMinutes, quantity, null, loyaltyPoints,
                vehicleTypeId);
    }

    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public int minutes() {
        return estimatedMinutes * quantity;
    }

    /** Puntos que gana el cliente por esta linea (por unidad de cantidad). */
    public int pointsEarned() {
        return loyaltyPoints * quantity;
    }
}
