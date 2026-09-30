package com.lavarapido.booking.domain.model;

// estados por los que pasa una reserva
public enum BookingStatus {
    PENDING,      // recién creada, sin confirmar
    CONFIRMED,    // confirmada por el lavadero
    IN_WASH,      // el vehículo está siendo lavado
    READY,        // listo para recoger
    COMPLETED,    // entregado al cliente
    CANCELED      // cancelada por el cliente o el lavadero
}
