package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Un servicio del catalogo con sus tarifas vigentes (una por tipo de vehiculo). El servicio no
 * tiene precio propio (2FN): el precio depende del par servicio + tipo de vehiculo.
 */
public record CatalogItem(int id, String code, String name, String description, ServiceCategory category,
                          boolean active, List<ServicePrice> prices) {

    public CatalogItem {
        prices = prices == null ? List.of() : List.copyOf(prices);
    }

    public Optional<ServicePrice> priceFor(short vehicleTypeId) {
        return prices.stream().filter(price -> price.vehicleTypeId() == vehicleTypeId).findFirst();
    }

    /** Codigo estable en mayusculas (BASIC, PREMIUM...). */
    public static String normalizeCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (code.isEmpty() || code.length() > 30) {
            throw new InvalidValueException("INVALID_SERVICE_CODE", "Service code must have 1 to 30 characters");
        }
        return code;
    }

    public static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new InvalidValueException("INVALID_SERVICE_NAME", "Service name must have 1 to 100 characters");
        }
        return name;
    }
}
