package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.port.in.CancelBookingUseCase;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.service.BookingPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// implementación del caso de uso para cancelar reservas
@Service
public class CancelBookingService implements CancelBookingUseCase {

    private final BookingRepository bookingRepository;

    public CancelBookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    @Transactional
    public Booking cancelBooking(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("La reserva no existe"));

        // verificar que la reserva pertenece al cliente
        if (!booking.customerId().equals(customerId)) {
            throw new IllegalArgumentException("No puedes cancelar una reserva que no es tuya");
        }

        // verificar que se pueda cancelar según las reglas de negocio
        if (!BookingPolicy.canCancel(booking.status())) {
            throw new IllegalStateException("No se puede cancelar una reserva en estado " + booking.status());
        }

        // cambiar el estado a CANCELED
        Booking canceled = new Booking(
                booking.bookingId(),
                booking.bookingCode(),
                booking.customerId(),
                booking.vehicleId(),
                booking.serviceOfferingId(),
                booking.locationId(),
                booking.bookingDate(),
                booking.bookingTime(),
                BookingStatus.CANCELED,
                booking.totalAmount(),
                booking.notes()
        );

        return bookingRepository.save(canceled);
    }
}
