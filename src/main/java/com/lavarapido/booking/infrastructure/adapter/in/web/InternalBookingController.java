package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.ActiveCountResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.BookingDtos.BookingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Reservas para otros servicios (/internal/**, llave X-Internal-Key; no pasa por el gateway).
 * operations-service las lee al asignar un operario solo (RF-008) y al mostrarle al cliente quién
 * está libre (RF-010), cuando no hay un usuario con token detrás. Van sin los datos del vehículo:
 * esos los da customer-service y aquí no hacen falta. customer-service usa active-count (BIZ-08).
 */
@RestController
@RequestMapping("/internal/v1/bookings")
class InternalBookingController {

    private final BookingUseCase bookings;

    InternalBookingController(BookingUseCase bookings) {
        this.bookings = bookings;
    }

    @GetMapping
    List<BookingResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return bookings.listForService(from, to).stream().map(view -> BookingResponse.from(view, false)).toList();
    }

    /**
     * BIZ-08: customer-service pregunta antes de retirar un vehiculo; si tiene reservas activas
     * (en curso, o programadas/confirmadas que no han terminado) no lo deja retirar.
     */
    @GetMapping("/active-count")
    ActiveCountResponse activeCount(@RequestParam long customerVehicleId) {
        return new ActiveCountResponse(customerVehicleId, bookings.activeBookingsOf(customerVehicleId));
    }

    @GetMapping("/{id}")
    BookingResponse get(@PathVariable long id) {
        return BookingResponse.from(bookings.getForService(id), false);
    }

    /** operations-service cierra como no asistio una confirmada vencida que nunca tuvo operario. */
    @PostMapping("/{id}/no-show")
    BookingResponse noShow(@PathVariable long id) {
        return BookingResponse.from(bookings.markUnattended(id), false);
    }
}
