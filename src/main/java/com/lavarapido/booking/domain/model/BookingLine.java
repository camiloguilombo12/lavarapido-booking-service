package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;

/**
 * Una linea de la reserva: apunta a la tarifa (service_price) con la que se cobro. El nombre y el
 * precio salen de esa fila, que nunca cambia (INV-BOOK-008). lineId es el booking_service_id: null
 * mientras la linea no se ha guardado. operations-service lo usa para la ejecucion de cada linea.
 *
 * loyaltyPoints es los puntos de fidelizacion que paga este servicio (catalog.service.loyalty_points),
 * leidos en el momento de armar la linea; a diferencia del precio no hay una fila historica para
 * congelarlos, asi que si el admin cambia los puntos de un servicio despues, las reservas viejas que
 * todavia no se han pagado toman el valor vigente cuando payment-service los acredite.
 */
public record BookingLine(long servicePriceId, int serviceId, String serviceCode, String serviceName,
                          BigDecimal price, short estimatedMinutes, short quantity, Long lineId, int loyaltyPoints) {

    /** Linea nueva, todavia sin guardar. */
    public BookingLine(long servicePriceId, int serviceId, String serviceCode, String serviceName,
                       BigDecimal price, short estimatedMinutes, short quantity, int loyaltyPoints) {
        this(servicePriceId, serviceId, serviceCode, serviceName, price, estimatedMinutes, quantity, null, loyaltyPoints);
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
