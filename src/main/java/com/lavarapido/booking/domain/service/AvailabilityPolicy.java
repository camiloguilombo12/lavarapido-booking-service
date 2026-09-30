package com.lavarapido.booking.domain.service;

import com.lavarapido.booking.domain.model.DayWindow;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Calcula en que horas de un dia cabe un servicio (RF-006, INV-BOOK-005 e INV-BOOK-006).
 *
 * Es codigo puro: recibe el horario del dia, las bahias activas y lo que ya esta ocupado, y no
 * toca la base. Asi la regla se prueba sin Spring y el frontend no tiene que calcular nada.
 *
 * Una hora sirve si:
 *  - el servicio empieza y termina dentro del horario del dia;
 *  - no se cruza con la pausa;
 *  - no ya paso (con la hora del lavadero);
 *  - hay al menos una bahia activa sin otra reserva que se cruce.
 */
public final class AvailabilityPolicy {

    private AvailabilityPolicy() {
    }

    /** Franja ya ocupada en una bahia, en hora local del lavadero. */
    public record Occupancy(short bayId, LocalDateTime start, LocalDateTime end) {
    }

    /** Hora de inicio posible y la bahia que tomaria (null si no hay libre). */
    public record Slot(LocalTime time, Short bayId) {

        public boolean available() {
            return bayId != null;
        }
    }

    /** Todas las horas del dia, cada stepMinutes, con la bahia libre de cada una. */
    public static List<Slot> slots(LocalDate date, Optional<DayWindow> window, List<Short> activeBays,
                                   List<Occupancy> taken, int durationMinutes, int stepMinutes,
                                   LocalDateTime now) {
        if (window.isEmpty() || durationMinutes <= 0 || stepMinutes <= 0) {
            return List.of();
        }
        DayWindow day = window.get();
        List<Slot> result = new ArrayList<>();
        LocalDateTime closes = date.atTime(day.closesAt());
        for (LocalDateTime start = date.atTime(day.opensAt()); ; start = start.plusMinutes(stepMinutes)) {
            LocalDateTime end = start.plusMinutes(durationMinutes);
            if (end.isAfter(closes) || !start.toLocalDate().equals(date)) {
                break;
            }
            if (crossesBreak(date, day, start, end)) {
                continue;
            }
            Short bay = start.isAfter(now) ? freeBay(start, end, activeBays, taken).orElse(null) : null;
            result.add(new Slot(start.toLocalTime(), bay));
        }
        return result;
    }

    /** La bahia libre de menor numero para esa franja, si hay alguna. */
    public static Optional<Short> freeBay(LocalDateTime start, LocalDateTime end, List<Short> activeBays,
                                          List<Occupancy> taken) {
        return activeBays.stream()
                .sorted()
                .filter(bay -> taken.stream().noneMatch(occupied -> occupied.bayId() == bay
                        && occupied.start().isBefore(end) && start.isBefore(occupied.end())))
                .findFirst();
    }

    /**
     * RF-006: hasta max horas libres del mismo dia, las mas cercanas a la pedida, en orden.
     */
    public static List<LocalTime> alternatives(List<Slot> slots, LocalTime requested, int max) {
        return slots.stream()
                .filter(Slot::available)
                .map(Slot::time)
                .filter(time -> !time.equals(requested))
                .sorted(Comparator.comparingLong(time -> Math.abs(Duration.between(requested, time).toMinutes())))
                .limit(max)
                .sorted()
                .toList();
    }

    private static boolean crossesBreak(LocalDate date, DayWindow day, LocalDateTime start, LocalDateTime end) {
        if (!day.hasBreak()) {
            return false;
        }
        LocalDateTime breakStart = date.atTime(day.breakStartsAt());
        LocalDateTime breakEnd = date.atTime(day.breakEndsAt());
        return start.isBefore(breakEnd) && breakStart.isBefore(end);
    }
}
