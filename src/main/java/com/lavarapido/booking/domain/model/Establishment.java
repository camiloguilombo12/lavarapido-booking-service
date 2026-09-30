package com.lavarapido.booking.domain.model;

/** Los datos del negocio (una sola fila): la sede donde el cliente lleva su vehiculo. */
public record Establishment(String legalName, String tradeName, String taxId, String address,
                            String phone, String email, String logoUrl) {
}
