package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingServiceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.CancellationReasonJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repositorios de reservas, sus lineas y los motivos de cancelacion. */
public final class BookingJpaRepositories {

    private BookingJpaRepositories() {
    }

    public interface Bookings extends JpaRepository<BookingJpaEntity, Long> {

        Optional<BookingJpaEntity> findByIdAndDeletedAtIsNull(long id);

        /** Las que ocupan bahia (1, 2, 3 = SCHEDULED, CONFIRMED, IN_PROGRESS) y se cruzan con [from, to). */
        @Query("""
                SELECT b FROM BookingJpaEntity b
                WHERE b.deletedAt IS NULL AND b.statusId IN (1, 2, 3)
                  AND b.scheduledStart < :to AND b.scheduledEnd > :from
                """)
        List<BookingJpaEntity> findOccupying(@Param("from") Instant from, @Param("to") Instant to);

        @Query("""
                SELECT b FROM BookingJpaEntity b
                WHERE b.deletedAt IS NULL
                  AND (b.bookedBy = :userId OR b.customerVehicleId IN :vehicleIds)
                ORDER BY b.scheduledStart DESC
                """)
        List<BookingJpaEntity> findForCustomer(@Param("userId") long userId,
                                               @Param("vehicleIds") Collection<Long> vehicleIds);

        List<BookingJpaEntity> findByBookedByAndDeletedAtIsNullOrderByScheduledStartDesc(long bookedBy);

        List<BookingJpaEntity> findByScheduledStartGreaterThanEqualAndScheduledStartLessThanAndDeletedAtIsNullOrderByScheduledStartAsc(
                Instant from, Instant to);

        List<BookingJpaEntity> findByScheduledStartGreaterThanEqualAndScheduledStartLessThanAndStatusIdAndDeletedAtIsNullOrderByScheduledStartAsc(
                Instant from, Instant to, short statusId);

        @Query("""
                SELECT COUNT(b) > 0 FROM BookingJpaEntity b
                WHERE b.deletedAt IS NULL AND b.serviceBayId = :bayId
                  AND b.statusId IN (1, 2, 3) AND b.scheduledEnd > :from
                """)
        boolean existsUpcomingOnBay(@Param("bayId") short bayId, @Param("from") Instant from);
    }

    public interface Lines extends JpaRepository<BookingServiceJpaEntity, Long> {

        List<BookingServiceJpaEntity> findByBookingIdInAndDeletedAtIsNull(Collection<Long> bookingIds);

        /** Incluye las borradas: uq_booking_service (booking_id, service_price_id) no es filtrado. */
        List<BookingServiceJpaEntity> findByBookingId(long bookingId);
    }

    public interface CancellationReasons extends JpaRepository<CancellationReasonJpaEntity, Short> {

        List<CancellationReasonJpaEntity> findByActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc();

        Optional<CancellationReasonJpaEntity> findByCodeAndActiveTrueAndDeletedAtIsNull(String code);
    }
}
