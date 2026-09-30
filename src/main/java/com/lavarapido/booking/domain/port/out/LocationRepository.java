package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.Location;

import java.util.List;
import java.util.Optional;

// puerto para consultar las sedes del lavadero
public interface LocationRepository {

    List<Location> findAllActive();

    Optional<Location> findById(Long locationId);
}
