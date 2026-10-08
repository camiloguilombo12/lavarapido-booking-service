package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.PriceDefinition;

import java.util.List;

/** Alta o edicion de un servicio del catalogo con su tarifa por tipo de vehiculo. */
public record ServiceCommand(String code, String name, String description, short categoryId,
                             List<PriceDefinition> prices, int loyaltyPoints) {
}
