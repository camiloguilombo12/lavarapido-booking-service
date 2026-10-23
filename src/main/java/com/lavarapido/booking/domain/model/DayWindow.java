package com.lavarapido.booking.domain.model;

import java.time.LocalTime;

/** Horario efectivo de un dia (ya con la excepcion aplicada). La pausa es opcional. */
public record DayWindow(LocalTime opensAt, LocalTime closesAt, LocalTime breakStartsAt, LocalTime breakEndsAt) {

    public boolean hasBreak() {
        return breakStartsAt != null && breakEndsAt != null;
    }
}
