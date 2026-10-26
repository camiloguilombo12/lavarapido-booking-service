package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.model.DaySummary;
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

    /** Para otros servicios por la llave interna (RF-008/010): sin los datos del vehículo. */
    BookingView getForService(long bookingId);

    List<BookingView> listForService(LocalDate from, LocalDate to);

    /**
     * BIZ-08: cuantas reservas activas tiene el vehiculo (en curso, o programadas/confirmadas que no
     * han terminado). customer-service lo pregunta antes de dejar retirar el vehiculo.
     */
    long activeBookingsOf(long customerVehicleId);

    /** Reservas de un día (por defecto hoy, en la hora del lavadero) frente al día anterior. Solo admin. */
    DaySummary daySummary(LocalDate date, Caller caller);

    /** Solo admin: IN_PROGRESS, COMPLETED, NO_SHOW, CONFIRMED o CANCELLED (con motivo). */
    BookingView changeStatus(long bookingId, BookingStatus status, String reasonCode, Caller caller);

    /**
     * TEMPORAL hasta que exista operations-service: reservas entre date y to (sin las canceladas ni
     * las que quedaron en no asistio)
     * para el operario. Sin to, solo ese dia. Sin asignacion por operario todavia, ve las de todo
     * el lavadero.
     */
    List<BookingView> operatorDay(LocalDate date, LocalDate to, Caller caller);

    /** TEMPORAL hasta operations-service: el operario solo empieza (IN_PROGRESS) o termina (COMPLETED). */
    BookingView operatorAdvance(long bookingId, BookingStatus status, Caller caller);

    List<CancellationReason> cancellationReasons();

    /**
     * operations-service la pide para una confirmada vencida que nunca tuvo operario: nadie pudo
     * atenderla, queda NO_SHOW. 409 BOOKING_NOT_EXPIRED si no esta confirmada o aun no vence.
     */
    BookingView markUnattended(long bookingId);

    /**
     * D: ids de las reservas DIGITAL activas sin comprobante cuyo plazo ya paso. PaymentDeadlineJob
     * las cancela una por una con cancelForPaymentTimeout.
     */
    List<Long> paymentOverdueBookings();

    /**
     * D: antes de cancelar le pregunta a payment-service si ya hay pago (adenda). Si lo hay, lo marca
     * como reportado y no cancela (PAYMENT_REPORTED). Si payment dice que no, cancela con PAYMENT_TIMEOUT
     * por el mismo camino que una cancelacion normal (publica BookingCancelled). Si la reserva ya no
     * vence, NOT_OVERDUE. Si payment no responde, DependencyUnavailableException y no se toca nada; un
     * choque de row_version sale como OptimisticLockingFailureException.
     */
    PaymentTimeoutOutcome cancelForPaymentTimeout(long bookingId);

    /**
     * payment-service avisa que el cliente reporto el pago (payment.reported) o que se aprobo
     * (payment.confirmed): se guarda payment_reported_at una sola vez.
     */
    PaymentReportOutcome recordPaymentReported(long bookingId);
}
