package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.Schedule;

import java.util.List;

// puerto para consultar los horarios de atención
public interface ScheduleRepository {

    List<Schedule> findByLocationId(Long locationId);
}
