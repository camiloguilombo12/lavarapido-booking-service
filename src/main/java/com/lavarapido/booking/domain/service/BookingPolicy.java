package com.lavarapido.booking.domain.service;

import com.lavarapido.booking.domain.model.BookingStatus;

import java.time.LocalDate;
import java.time.LocalTime;

// reglas de negocio para las reservas
public final class BookingPolicy {

    private BookingPolicy() {}

    // una reserva no puede ser para una fecha pasada
    public static boolean isDateValid(LocalDate date) {
        return !date.isBefore(LocalDate.now());
    }

    // la hora de la reserva debe estar dentro del horario de atención
    public static boolean isTimeValid(LocalTime time, LocalTime open, LocalTime close) {
        return !time.isBefore(open) && !time.isAfter(close);
    }

    // solo se puede cancelar una reserva que no esté en lavado o completada
    public static boolean canCancel(BookingStatus status) {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
    }

    // el código de reserva es único y tiene formato RES-XXXXX
    public static String generateBookingCode(long sequence) {
        return "RES-" + String.format("%05d", sequence);
    }
}
