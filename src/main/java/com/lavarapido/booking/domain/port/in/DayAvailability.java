package com.lavarapido.booking.domain.port.in;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Horas de un dia en que cabe el servicio pedido. open = false es un dia cerrado. */
public record DayAvailability(LocalDate date, boolean open, int durationMinutes, List<TimeSlot> slots) {

    public record TimeSlot(LocalTime time, boolean available) {
    }
}
