package com.lavarapido.booking.domain.model;

// sede del lavadero donde se presta el servicio
public record Location(
    Long locationId,
    String name,
    String address,
    String phone,
    boolean isActive
) {}
