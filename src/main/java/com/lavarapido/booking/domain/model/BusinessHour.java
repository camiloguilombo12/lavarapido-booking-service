package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.time.LocalTime;
import java.util.Optional;

/**
 * Horario de un dia de la semana (1 = lunes ... 7 = domingo). working = false es un dia cerrado;
 * igual se guardan sus horas para que el admin no las pierda al volver a abrirlo.
 */
public record BusinessHour(short dayOfWeek, boolean working, LocalTime opensAt, LocalTime closesAt,
                           LocalTime breakStartsAt, LocalTime breakEndsAt) {

    public BusinessHour {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new InvalidValueException("INVALID_DAY", "Day of week must be between 1 and 7");
        }
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
            throw new InvalidValueException("INVALID_HOURS", "Closing time must be after opening time");
        }
        if ((breakStartsAt == null) != (breakEndsAt == null)) {
            throw new InvalidValueException("INVALID_BREAK", "A break needs both start and end");
        }
        if (breakStartsAt != null && (!breakStartsAt.isAfter(opensAt) || !breakEndsAt.isBefore(closesAt)
                || !breakEndsAt.isAfter(breakStartsAt))) {
            throw new InvalidValueException("INVALID_BREAK", "The break must fall inside the opening hours");
        }
    }

    public Optional<DayWindow> window() {
        return working ? Optional.of(new DayWindow(opensAt, closesAt, breakStartsAt, breakEndsAt)) : Optional.empty();
    }
}
