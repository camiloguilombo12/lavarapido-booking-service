package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingStatus;

import java.util.List;
import java.util.Optional;

// puerto para guardar y consultar reservas
public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(Long bookingId);

    Optional<Booking> findByCode(String bookingCode);

    List<Booking> findByCustomerId(Long customerId);

    List<Booking> findByStatus(BookingStatus status);

    boolean existsByCode(String bookingCode);
}
