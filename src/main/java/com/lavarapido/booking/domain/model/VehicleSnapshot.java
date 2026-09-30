package com.lavarapido.booking.domain.model;

/**
 * Lo que booking necesita de un vehiculo de customer-service (REST, ADR-004). No se guarda aqui:
 * se consulta cuando hace falta, asi un cambio de placa se ve en todas las reservas.
 * ownerUserId solo viene en las consultas del admin.
 */
public record VehicleSnapshot(long id, String licensePlate, String licensePlateFormatted, String vehicleType,
                              short vehicleTypeId, String vehicleTypeName, String brand, String model,
                              Long ownerUserId) {
}
