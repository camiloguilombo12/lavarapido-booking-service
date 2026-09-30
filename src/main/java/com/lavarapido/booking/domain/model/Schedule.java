package com.lavarapido.booking.domain.model;

import java.time.LocalTime;

// horario de atención de una sede en un día de la semana
public record Schedule(
    Long scheduleId,
    Long locationId,
    Integer dayOfWeek,        // 1=lunes ... 7=domingo
    LocalTime startTime,
    LocalTime endTime,
    Integer slotDurationMinutes,
    boolean isActive
) {}
