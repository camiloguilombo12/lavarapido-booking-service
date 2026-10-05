package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingLine;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.model.CancellationReason;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingServiceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.CancellationReasonJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServicePriceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.BookingJpaRepositories;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServiceJpaRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServicePriceJpaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reservas y sus lineas. Una reserva se lee junto con sus lineas, la tarifa de cada una (para el
 * precio congelado) y el nombre del servicio.
 */
@Repository
class BookingPersistenceAdapter implements BookingRepository {

    /**
     * sp_getapplock con dueño Transaction: el candado se suelta solo al terminar la transaccion.
     * Devuelve >= 0 si lo obtuvo; negativo si se vencio la espera (10 s).
     */
    private static final String LOCK_SQL = """
            SET NOCOUNT ON;
            DECLARE @result INT;
            EXEC @result = sp_getapplock @Resource = 'booking-schedule', @LockMode = 'Exclusive',
                                         @LockOwner = 'Transaction', @LockTimeout = 10000;
            SELECT @result;
            """;

    private final BookingJpaRepositories.Bookings bookings;
    private final BookingJpaRepositories.Lines lines;
    private final BookingJpaRepositories.CancellationReasons reasons;
    private final ServicePriceJpaRepository prices;
    private final ServiceJpaRepository services;
    private final EntityManager entityManager;

    BookingPersistenceAdapter(BookingJpaRepositories.Bookings bookings, BookingJpaRepositories.Lines lines,
                              BookingJpaRepositories.CancellationReasons reasons, ServicePriceJpaRepository prices,
                              ServiceJpaRepository services, EntityManager entityManager) {
        this.bookings = bookings;
        this.lines = lines;
        this.reasons = reasons;
        this.prices = prices;
        this.services = services;
        this.entityManager = entityManager;
    }

    @Override
    public void lockSchedule() {
        Object result = entityManager.createNativeQuery(LOCK_SQL).getSingleResult();
        if (result instanceof Number number && number.intValue() < 0) {
            throw new ConflictException("SCHEDULE_BUSY", "The schedule is busy, try again");
        }
    }

    @Override
    public List<Booking> findOccupying(Instant from, Instant to) {
        return toDomain(bookings.findOccupying(from, to));
    }

    @Override
    public Booking save(Booking booking, long actor) {
        BookingJpaEntity entity = booking.id() == 0L ? newEntity(booking, actor)
                : bookings.findById(booking.id()).orElseThrow();
        entity.setServiceBayId(booking.serviceBayId());
        entity.setScheduledStart(booking.scheduledStart());
        entity.setScheduledEnd(booking.scheduledEnd());
        entity.setStatusId(booking.status().id());
        entity.setCancellationReasonId(booking.cancellationReason() == null ? null : booking.cancellationReason().id());
        entity.setNotes(booking.notes());
        entity.setUpdatedBy(actor);
        BookingJpaEntity saved = bookings.saveAndFlush(entity);
        if (booking.id() == 0L) {
            booking.assignId(saved.getId());
        }
        saveLines(saved.getId(), booking.lines(), actor);
        return booking;
    }

    @Override
    public Optional<Booking> findById(long bookingId) {
        return bookings.findByIdAndDeletedAtIsNull(bookingId).map(entity -> toDomain(List.of(entity)).getFirst());
    }

    @Override
    public List<Booking> findForCustomer(long userId, Collection<Long> vehicleIds) {
        // un IN con lista vacia no es SQL valido: sin vehiculos solo cuentan las que hizo el mismo
        return toDomain(vehicleIds.isEmpty()
                ? bookings.findByBookedByAndDeletedAtIsNullOrderByScheduledStartDesc(userId)
                : bookings.findForCustomer(userId, vehicleIds));
    }

    @Override
    public List<Booking> findStartingBetween(Instant from, Instant to, BookingStatus status) {
        return toDomain(status == null
                ? bookings.findByScheduledStartGreaterThanEqualAndScheduledStartLessThanAndDeletedAtIsNullOrderByScheduledStartAsc(from, to)
                : bookings.findByScheduledStartGreaterThanEqualAndScheduledStartLessThanAndStatusIdAndDeletedAtIsNullOrderByScheduledStartAsc(
                        from, to, status.id()));
    }

    @Override
    public boolean hasUpcomingOnBay(short bayId, Instant from) {
        return bookings.existsUpcomingOnBay(bayId, from);
    }

    @Override
    public List<CancellationReason> findCancellationReasons() {
        return reasons.findByActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc().stream()
                .map(BookingPersistenceAdapter::toReason)
                .toList();
    }

    @Override
    public Optional<CancellationReason> findCancellationReason(String code) {
        return reasons.findByCodeAndActiveTrueAndDeletedAtIsNull(code).map(BookingPersistenceAdapter::toReason);
    }

    private static BookingJpaEntity newEntity(Booking booking, long actor) {
        BookingJpaEntity entity = new BookingJpaEntity();
        entity.setCustomerVehicleId(booking.customerVehicleId());
        entity.setBookedBy(booking.bookedBy());
        entity.setPointsRedeemed(booking.pointsRedeemed());
        entity.setPointsDiscountAmount(booking.pointsDiscountAmount());
        entity.setCreatedBy(actor);
        return entity;
    }

    /**
     * Deja en la base exactamente estas lineas. uq_booking_service (booking, tarifa) no es
     * filtrado, asi que una linea que vuelve se revive en vez de insertarse otra vez.
     */
    private void saveLines(long bookingId, List<BookingLine> wanted, long actor) {
        Map<Long, BookingServiceJpaEntity> existing = lines.findByBookingId(bookingId).stream()
                .collect(Collectors.toMap(BookingServiceJpaEntity::getServicePriceId, Function.identity()));
        Set<Long> wantedPrices = wanted.stream().map(BookingLine::servicePriceId).collect(Collectors.toSet());

        for (BookingServiceJpaEntity line : existing.values()) {
            if (!wantedPrices.contains(line.getServicePriceId()) && line.getDeletedAt() == null) {
                line.setDeletedAt(Instant.now());
                line.setDeletedBy(actor);
                lines.save(line);
            }
        }
        for (BookingLine line : wanted) {
            BookingServiceJpaEntity entity = existing.get(line.servicePriceId());
            if (entity == null) {
                entity = new BookingServiceJpaEntity();
                entity.setBookingId(bookingId);
                entity.setServicePriceId(line.servicePriceId());
                entity.setCreatedBy(actor);
            } else if (entity.getDeletedAt() == null && entity.getQuantity() == line.quantity()) {
                continue;
            }
            entity.setQuantity(line.quantity());
            entity.setDeletedAt(null);
            entity.setDeletedBy(null);
            entity.setUpdatedBy(actor);
            lines.save(entity);
        }
    }

    /** Arma las reservas con sus lineas en pocas consultas (no una por reserva). */
    private List<Booking> toDomain(List<BookingJpaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Long, List<BookingServiceJpaEntity>> linesByBooking = lines
                .findByBookingIdInAndDeletedAtIsNull(entities.stream().map(BookingJpaEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(BookingServiceJpaEntity::getBookingId));
        Map<Long, ServicePriceJpaEntity> priceById = prices
                .findAllById(linesByBooking.values().stream().flatMap(List::stream)
                        .map(BookingServiceJpaEntity::getServicePriceId).distinct().toList()).stream()
                .collect(Collectors.toMap(ServicePriceJpaEntity::getId, Function.identity()));
        Map<Integer, ServiceJpaEntity> serviceById = services
                .findAllById(priceById.values().stream().map(ServicePriceJpaEntity::getServiceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ServiceJpaEntity::getId, Function.identity()));
        Map<Short, CancellationReason> reasonById = reasons.findAll().stream()
                .collect(Collectors.toMap(CancellationReasonJpaEntity::getId, BookingPersistenceAdapter::toReason));

        List<Booking> result = new ArrayList<>();
        for (BookingJpaEntity entity : entities) {
            List<BookingLine> bookingLines = linesByBooking.getOrDefault(entity.getId(), List.of()).stream()
                    .map(line -> toLine(line, priceById.get(line.getServicePriceId()), serviceById))
                    .toList();
            result.add(Booking.restore(entity.getId(), entity.getCustomerVehicleId(), entity.getServiceBayId(),
                    entity.getScheduledStart(), entity.getScheduledEnd(), BookingStatus.ofId(entity.getStatusId()),
                    entity.getBookedBy(),
                    entity.getCancellationReasonId() == null ? null : reasonById.get(entity.getCancellationReasonId()),
                    entity.getPointsRedeemed() == null ? 0 : entity.getPointsRedeemed(),
                    entity.getPointsDiscountAmount() == null ? BigDecimal.ZERO : entity.getPointsDiscountAmount(),
                    entity.getNotes(), bookingLines, entity.getCreatedAt()));
        }
        return result;
    }

    private static BookingLine toLine(BookingServiceJpaEntity line, ServicePriceJpaEntity price,
                                      Map<Integer, ServiceJpaEntity> serviceById) {
        ServiceJpaEntity service = serviceById.get(price.getServiceId());
        return new BookingLine(price.getId(), price.getServiceId(), service == null ? null : service.getCode(),
                service == null ? null : service.getName(), price.getPrice(), price.getEstimatedMinutes(),
                line.getQuantity(), line.getId());
    }

    private static CancellationReason toReason(CancellationReasonJpaEntity entity) {
        return new CancellationReason(entity.getId(), entity.getCode(), entity.getName(),
                Boolean.TRUE.equals(entity.getCustomerFault()));
    }
}
