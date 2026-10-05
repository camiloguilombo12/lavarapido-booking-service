package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.BookingResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.StatusRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * TEMPORAL: pantalla del operario mientras operations-service no existe. Roles OPERATOR o ADMIN
 * (SecurityConfig). Cuando llegue la asignacion real, esto se reemplaza por los endpoints de
 * operations-service y se borra.
 */
@RestController
@RequestMapping("/api/v1/operator/bookings")
class OperatorBookingController {

    private final BookingUseCase bookings;

    OperatorBookingController(BookingUseCase bookings) {
        this.bookings = bookings;
    }

    /** Reservas del dia (hoy si no se manda date) o entre date y to, sin las canceladas. */
    @GetMapping
    List<BookingResponse> day(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) LocalDate date,
                              @RequestParam(required = false) LocalDate to) {
        return bookings.operatorDay(date, to, AuthenticatedUser.operator(jwt)).stream()
                .map(view -> BookingResponse.from(view, true))
                .toList();
    }

    /** Una reserva (operations-service la lee con el token del operario para iniciarla o terminarla). */
    @GetMapping("/{id}")
    BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        // el operario ve las reservas como personal del lavadero (lectura)
        return BookingResponse.from(bookings.get(id, AuthenticatedUser.admin(jwt)), true);
    }

    /** Solo IN_PROGRESS (empezar) o COMPLETED (terminar). */
    @PatchMapping("/{id}/status")
    BookingResponse advance(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                            @Valid @RequestBody StatusRequest request) {
        return BookingResponse.from(bookings.operatorAdvance(id, BookingStatus.of(request.status()),
                AuthenticatedUser.operator(jwt)), true);
    }
}
