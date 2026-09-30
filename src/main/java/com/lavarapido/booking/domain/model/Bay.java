package com.lavarapido.booking.domain.model;

// bahía de trabajo dentro de una sede
public record Bay(
    Long bayId,
    Long locationId,
    String name,
    boolean isActive
) {}
