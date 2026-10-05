package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.model.CancellationReason;

import java.time.LocalDate;
import java.util.List;

/**
 * Reservas. Un cliente solo ve y cambia las suyas; el admin, todas. El id del cliente nunca llega
 * en la peticion: sale del token (Caller).
 */
public interface BookingUseCase {

    /** Horas libres de un dia para esos servicios y ese tipo de vehiculo (RF-006). */
    DayAvailability availability(LocalDate date, short vehicleTypeId, List<Integer> serviceIds, Long excludeBookingId);

    /** Crea la reserva confirmada con bahia, o lanza SlotUnavailableException con alternativas. */
    BookingView create(BookingRequest request, Caller caller);

    List<BookingView> mine(Caller caller);

    BookingView get(long bookingId, Caller caller);

    BookingView reschedule(long bookingId, BookingRequest request, Caller caller);

    BookingView cancel(long bookingId, String reasonCode, Caller caller);

    /** Solo admin: reservas que empiezan entre from y to (fechas locales, ambas incluidas). */
    List<BookingView> list(LocalDate from, LocalDate to, BookingStatus status, Caller caller);

    /** Solo admin: IN_PROGRESS, COMPLETED, NO_SHOW, CONFIRMED o CANCELLED (con motivo). */
    BookingView changeStatus(long bookingId, BookingStatus status, String reasonCode, Caller caller);

    /**
     * TEMPORAL hasta que exista operations-service: reservas entre date y to (sin las canceladas)
     * para el operario. Sin to, solo ese dia. Sin asignacion por operario todavia, ve las de todo
     * el lavadero.
     */
    List<BookingView> operatorDay(LocalDate date, LocalDate to, Caller caller);

    /** TEMPORAL hasta operations-service: el operario solo empieza (IN_PROGRESS) o termina (COMPLETED). */
    BookingView operatorAdvance(long bookingId, BookingStatus status, Caller caller);

    List<CancellationReason> cancellationReasons();
}
