package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.math.BigDecimal;

/** Precio y duracion que el admin fija para un tipo de vehiculo. */
public record PriceDefinition(short vehicleTypeId, BigDecimal price, short estimatedMinutes) {

    private static final int MAX_MINUTES = 600;
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    public PriceDefinition {
        if (vehicleTypeId <= 0) {
            throw new InvalidValueException("INVALID_VEHICLE_TYPE", "Vehicle type is required");
        }
        if (price == null || price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new InvalidValueException("INVALID_PRICE", "Price must be zero or more");
        }
        if (estimatedMinutes <= 0 || estimatedMinutes > MAX_MINUTES) {
            throw new InvalidValueException("INVALID_DURATION",
                    "Duration must be between 1 and " + MAX_MINUTES + " minutes");
        }
    }

    /** true si la tarifa vigente ya tiene este mismo precio y duracion (no hace falta fila nueva). */
    public boolean sameAs(ServicePrice current) {
        return current.price().compareTo(price) == 0 && current.estimatedMinutes() == estimatedMinutes;
    }
}
