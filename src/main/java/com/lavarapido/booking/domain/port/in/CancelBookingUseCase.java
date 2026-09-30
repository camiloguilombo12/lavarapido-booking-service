package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.Booking;

// caso de uso para cancelar una reserva existente
public interface CancelBookingUseCase {

    Booking cancelBooking(Long bookingId, Long customerId);
}
