package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.Bay;

import java.util.List;

// puerto para consultar las bahías de trabajo
public interface BayRepository {

    List<Bay> findByLocationId(Long locationId);
}
