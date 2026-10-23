package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.ServiceBay;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bahias del lavadero (sin las borradas). */
public interface BayRepository {

    List<ServiceBay> findAll();

    Optional<ServiceBay> findById(short bayId);

    boolean existsName(String name, Short exceptBayId);

    /** Crea la bahia con un codigo nuevo (BAY-05...) y la devuelve. */
    ServiceBay insert(String name, BayStatus status, long actor);

    void update(short bayId, String name, BayStatus status, long actor);

    void softDelete(short bayId, long actor, Instant now);
}
