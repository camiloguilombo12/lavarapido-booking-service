package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.VehicleSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas a customer-service (REST, ADR-004). Se hacen con el token de quien llama: customer
 * solo le devuelve a un cliente sus propios vehiculos, y al admin los de todos.
 */
public interface CustomerDirectory {

    /** Un vehiculo del cliente que llama; vacio si no existe o es de otro (INV-BOOK-002). */
    Optional<VehicleSnapshot> ownVehicle(long vehicleId);

    /** Los vehiculos activos del cliente que llama. */
    List<VehicleSnapshot> ownVehicles();

    /** Solo admin: vehiculos por id, con el user_id de su dueño. Los que no existen no vienen. */
    Map<Long, VehicleSnapshot> vehiclesByIds(Collection<Long> vehicleIds);
}
