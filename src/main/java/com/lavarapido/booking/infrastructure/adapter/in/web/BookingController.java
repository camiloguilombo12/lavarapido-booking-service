package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.application.usecase.CreateBookingService;
import com.lavarapido.booking.application.usecase.ListBookingsService;
import com.lavarapido.booking.application.usecase.CancelBookingService;
import com.lavarapido.booking.domain.model.Booking;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// controlador REST para las reservas
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final CreateBookingService createBookingService;
    private final ListBookingsService listBookingsService;
    private final CancelBookingService cancelBookingService;

    public BookingController(CreateBookingService createBookingService,
                             ListBookingsService listBookingsService,
                             CancelBookingService cancelBookingService) {
        this.createBookingService = createBookingService;
        this.listBookingsService = listBookingsService;
        this.cancelBookingService = cancelBookingService;
    }

    // crear una reserva nueva
    @PostMapping
    public ResponseEntity<Booking> create(@RequestBody CreateBookingRequest request) {
        var command = new CreateBookingService.CreateBookingCommand(
                request.customerId(),
                request.vehicleId(),
                request.serviceOfferingId(),
                request.locationId(),
                request.bookingDate(),
                request.bookingTime(),
                request.notes()
        );
        Booking booking = createBookingService.createBooking(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    // listar reservas de un cliente
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Booking>> listByCustomer(@PathVariable Long customerId) {
        List<Booking> bookings = listBookingsService.listByCustomer(customerId);
        return ResponseEntity.ok(bookings);
    }

    // cancelar una reserva
    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<Booking> cancel(@PathVariable Long bookingId, @RequestParam Long customerId) {
        Booking canceled = cancelBookingService.cancelBooking(bookingId, customerId);
        return ResponseEntity.ok(canceled);
    }

    // cuerpo de la petición para crear una reserva
    public record CreateBookingRequest(
            Long customerId,
            Long vehicleId,
            Long serviceOfferingId,
            Long locationId,
            String bookingDate,
            String bookingTime,
            String notes
    ) {}
}
