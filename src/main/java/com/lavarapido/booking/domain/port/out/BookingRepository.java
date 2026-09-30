package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.model.CancellationReason;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Reservas y sus lineas (esquema booking). */
public interface BookingRepository {

    /**
     * Bloquea la agenda hasta que termine la transaccion. Dos clientes que piden la misma hora al
     * tiempo se atienden uno detras del otro, asi no se asigna la misma bahia dos veces.
     */
    void lockSchedule();

    /** Reservas que ocupan bahia (SCHEDULED, CONFIRMED, IN_PROGRESS) y se cruzan con [from, to). */
    List<Booking> findOccupying(Instant from, Instant to);

    Booking save(Booking booking, long actor);

    Optional<Booking> findById(long bookingId);

    /** Reservas de un cliente: las que hizo el mismo o las de sus vehiculos. Mas recientes primero. */
    List<Booking> findForCustomer(long userId, Collection<Long> vehicleIds);

    /** Reservas que empiezan en [from, to), opcionalmente de un estado, por hora de inicio. */
    List<Booking> findStartingBetween(Instant from, Instant to, BookingStatus status);

    boolean hasUpcomingOnBay(short bayId, Instant from);

    List<CancellationReason> findCancellationReasons();

    Optional<CancellationReason> findCancellationReason(String code);
}
