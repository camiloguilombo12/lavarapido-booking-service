package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controlador REST para consultar reservas
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingQueryController {

    private final BookingRepository bookingRepository;

    public BookingQueryController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    // obtener detalle de una reserva por su id
    @GetMapping("/{bookingId}")
    public ResponseEntity<Booking> getById(@PathVariable Long bookingId) {
        return bookingRepository.findById(bookingId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // obtener detalle de una reserva por su código
    @GetMapping("/code/{bookingCode}")
    public ResponseEntity<Booking> getByCode(@PathVariable String bookingCode) {
        return bookingRepository.findByCode(bookingCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
