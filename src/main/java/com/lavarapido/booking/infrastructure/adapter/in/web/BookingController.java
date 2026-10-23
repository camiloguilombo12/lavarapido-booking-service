package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.domain.port.in.BookingView;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.AvailabilityResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.BookingResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.CancelRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.CreateBookingRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.ReasonResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.RescheduleRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

/**
 * Reservas del cliente que llama. Ninguna ruta recibe el id del cliente: sale del token, y una
 * reserva de otro responde 404.
 */
@RestController
@RequestMapping("/api/v1/bookings")
class BookingController {

    private final BookingUseCase bookings;

    BookingController(BookingUseCase bookings) {
        this.bookings = bookings;
    }

    /** Horas libres de un dia para esos servicios y ese tipo de vehiculo (RF-006). */
    @GetMapping("/availability")
    AvailabilityResponse availability(@RequestParam LocalDate date,
                                      @RequestParam short vehicleTypeId,
                                      @RequestParam List<Integer> serviceIds,
                                      @RequestParam(required = false) Long excludeBookingId) {
        return AvailabilityResponse.from(bookings.availability(date, vehicleTypeId, serviceIds, excludeBookingId));
    }

    /**
     * 201 con la reserva ya confirmada y su bahia. Si la hora esta ocupada: 409 SLOT_UNAVAILABLE
     * con hasta 5 horas alternativas del mismo dia (no se guarda nada).
     */
    @PostMapping
    ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody CreateBookingRequest request) {
        BookingView created = bookings.create(request.toRequest(), AuthenticatedUser.customer(jwt));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/bookings/{id}").build(created.booking().id()))
                .body(BookingResponse.from(created, false));
    }

    @GetMapping("/me")
    List<BookingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return bookings.mine(AuthenticatedUser.customer(jwt)).stream()
                .map(view -> BookingResponse.from(view, false))
                .toList();
    }

    @GetMapping("/cancellation-reasons")
    List<ReasonResponse> cancellationReasons() {
        return bookings.cancellationReasons().stream().map(ReasonResponse::from).toList();
    }

    @GetMapping("/{id}")
    BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return BookingResponse.from(bookings.get(id, AuthenticatedUser.customer(jwt)), false);
    }

    /** Cambiar fecha, hora o servicios (RF-007: solo antes de la hora de inicio). */
    @PutMapping("/{id}")
    BookingResponse reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                               @Valid @RequestBody RescheduleRequest request) {
        return BookingResponse.from(bookings.reschedule(id, request.toRequest(), AuthenticatedUser.customer(jwt)), false);
    }

    /** El cliente cancela con el motivo CUSTOMER_REQUEST; el cuerpo es opcional. */
    @PostMapping("/{id}/cancel")
    BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                           @RequestBody(required = false) CancelRequest request) {
        return BookingResponse.from(bookings.cancel(id, null, AuthenticatedUser.customer(jwt)), false);
    }
}
