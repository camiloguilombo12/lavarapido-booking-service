package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.Booking;

// caso de uso para crear una reserva nueva
public interface CreateBookingUseCase {

    Booking createBooking(CreateBookingCommand command);

    // datos necesarios para crear una reserva
    record CreateBookingCommand(
        Long customerId,
        Long vehicleId,
        Long serviceOfferingId,
        Long locationId,
        String bookingDate,
        String bookingTime,
        String notes
    ) {}
}
