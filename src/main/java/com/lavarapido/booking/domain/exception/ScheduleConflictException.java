package com.lavarapido.booking.domain.exception;

import java.util.List;

/**
 * BIZ-10: cambiar el horario o una excepcion, o sacar una bahia de servicio, deja reservas activas por
 * fuera (409 SCHEDULE_CONFLICTS_BOOKINGS). Lleva cuantas son y sus codigos (los primeros), para que
 * el admin las mueva o las cancele antes de volver a intentarlo.
 */
public class ScheduleConflictException extends ConflictException {

    private static final int MAX_CODES = 20;

    private final int conflictingBookings;
    private final List<String> bookingCodes;

    public ScheduleConflictException(List<String> bookingCodes) {
        super("SCHEDULE_CONFLICTS_BOOKINGS",
                bookingCodes.size() + " active booking(s) would fall outside the new schedule");
        this.conflictingBookings = bookingCodes.size();
        this.bookingCodes = List.copyOf(bookingCodes.subList(0, Math.min(bookingCodes.size(), MAX_CODES)));
    }

    public int conflictingBookings() {
        return conflictingBookings;
    }

    public List<String> bookingCodes() {
        return bookingCodes;
    }
}
