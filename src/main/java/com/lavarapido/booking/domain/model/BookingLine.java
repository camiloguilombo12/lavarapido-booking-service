package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;

/**
 * Una linea de la reserva: apunta a la tarifa (service_price) con la que se cobro. El nombre y el
 * precio salen de esa fila, que nunca cambia (INV-BOOK-008).
 */
public record BookingLine(long servicePriceId, int serviceId, String serviceCode, String serviceName,
                          BigDecimal price, short estimatedMinutes, short quantity) {

    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public int minutes() {
        return estimatedMinutes * quantity;
    }
}
