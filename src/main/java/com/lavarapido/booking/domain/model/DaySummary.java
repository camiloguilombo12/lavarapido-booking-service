package com.lavarapido.booking.domain.model;

import java.time.LocalDate;

/**
 * Reservas de un día del lavadero (en su zona horaria) frente al día anterior, para el inicio del
 * administrador. Cuenta todas las reservas con fecha de ese día, igual que la tarjeta "Reservas de hoy".
 *
 * @param difference reservas de ese día menos las del día anterior (negativa si hay menos)
 */
public record DaySummary(LocalDate date, int bookings, int previousDayBookings, int difference) {

    public static DaySummary of(LocalDate date, int bookings, int previousDayBookings) {
        return new DaySummary(date, bookings, previousDayBookings, bookings - previousDayBookings);
    }
}
