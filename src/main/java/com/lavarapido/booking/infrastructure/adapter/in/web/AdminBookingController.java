package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.domain.port.in.BookingView;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.BookingResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.CreateBookingRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.RescheduleRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.StatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

/** Reservas de todos los clientes (pantalla "Reservas" del admin). Solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/bookings")
class AdminBookingController {

    private final BookingUseCase bookings;

    AdminBookingController(BookingUseCase bookings) {
        this.bookings = bookings;
    }

    /** Reservas que empiezan entre from y to (fechas locales, incluidas). Sin fechas: las de hoy. */
    @GetMapping
    List<BookingResponse> list(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(required = false) String status) {
        BookingStatus filter = status == null || status.isBlank() ? null : BookingStatus.of(status);
        return bookings.list(from, to, filter, AuthenticatedUser.admin(jwt)).stream()
                .map(view -> BookingResponse.from(view, true))
                .toList();
    }

    @GetMapping("/{id}")
    BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return BookingResponse.from(bookings.get(id, AuthenticatedUser.admin(jwt)), true);
    }

    /** El admin reserva a nombre de un cliente: vehicleId es de ese cliente (lo busca por placa en customer). */
    @PostMapping
    ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody CreateBookingRequest request) {
        BookingView created = bookings.create(request.toRequest(), AuthenticatedUser.admin(jwt));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/admin/bookings/{id}").build(created.booking().id()))
                .body(BookingResponse.from(created, true));
    }

    @PutMapping("/{id}")
    BookingResponse reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                               @Valid @RequestBody RescheduleRequest request) {
        return BookingResponse.from(bookings.reschedule(id, request.toRequest(), AuthenticatedUser.admin(jwt)), true);
    }

    /** IN_PROGRESS, COMPLETED, NO_SHOW, CONFIRMED o CANCELLED (con reasonCode). */
    @PatchMapping("/{id}/status")
    BookingResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                 @Valid @RequestBody StatusRequest request) {
        return BookingResponse.from(bookings.changeStatus(id, BookingStatus.of(request.status()), request.reasonCode(),
                AuthenticatedUser.admin(jwt)), true);
    }
}
