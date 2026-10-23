package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/** Festivo o jornada especial: cerrado todo el dia, o abierto con otro horario (sin pausa). */
public record HoursException(int id, LocalDate date, boolean closed, LocalTime opensAt, LocalTime closesAt,
                             String reason) {

    public HoursException {
        if (date == null) {
            throw new InvalidValueException("INVALID_DATE", "Date is required");
        }
        reason = reason == null ? "" : reason.trim();
        if (reason.isEmpty() || reason.length() > 120) {
            throw new InvalidValueException("INVALID_REASON", "Reason must have 1 to 120 characters");
        }
        if (closed) {
            opensAt = null;
            closesAt = null;
        } else if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
            throw new InvalidValueException("INVALID_HOURS", "Closing time must be after opening time");
        }
    }

    public Optional<DayWindow> window() {
        return closed ? Optional.empty() : Optional.of(new DayWindow(opensAt, closesAt, null, null));
    }
}
