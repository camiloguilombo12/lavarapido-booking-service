package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;

/**
 * Una linea de la reserva: apunta a la tarifa (service_price) con la que se cobro. El nombre y el
 * precio salen de esa fila, que nunca cambia (INV-BOOK-008). lineId es el booking_service_id: null
 * mientras la linea no se ha guardado. operations-service lo usa para la ejecucion de cada linea.
 */
public record BookingLine(long servicePriceId, int serviceId, String serviceCode, String serviceName,
                          BigDecimal price, short estimatedMinutes, short quantity, Long lineId) {

    /** Linea nueva, todavia sin guardar. */
    public BookingLine(long servicePriceId, int serviceId, String serviceCode, String serviceName,
                       BigDecimal price, short estimatedMinutes, short quantity) {
        this(servicePriceId, serviceId, serviceCode, serviceName, price, estimatedMinutes, quantity, null);
    }

    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public int minutes() {
        return estimatedMinutes * quantity;
    }
}
