package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.port.in.ListBookingsUseCase;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// implementación del caso de uso para listar reservas de un cliente
@Service
public class ListBookingsService implements ListBookingsUseCase {

    private final BookingRepository bookingRepository;

    public ListBookingsService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> listByCustomer(Long customerId) {
        return bookingRepository.findByCustomerId(customerId);
    }
}
