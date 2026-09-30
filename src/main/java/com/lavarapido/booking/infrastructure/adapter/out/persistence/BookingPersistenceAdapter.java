package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.BookingJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// adaptador que implementa el puerto BookingRepository usando JPA
@Component
public class BookingPersistenceAdapter implements BookingRepository {

    private final BookingJpaRepository jpaRepository;

    public BookingPersistenceAdapter(BookingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Booking save(Booking booking) {
        BookingJpaEntity entity = toEntity(booking);
        BookingJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Booking> findById(Long bookingId) {
        return jpaRepository.findById(bookingId).map(this::toDomain);
    }

    @Override
    public Optional<Booking> findByCode(String bookingCode) {
        return jpaRepository.findByBookingCode(bookingCode).map(this::toDomain);
    }

    @Override
    public List<Booking> findByCustomerId(Long customerId) {
        return jpaRepository.findByCustomerIdAndDeletedAtIsNull(customerId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) {
        return jpaRepository.findByStatusAndDeletedAtIsNull(status.name())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCode(String bookingCode) {
        return jpaRepository.existsByBookingCode(bookingCode);
    }

    // convierte de entidad JPA a modelo de dominio
    private Booking toDomain(BookingJpaEntity entity) {
        return new Booking(
                entity.getBookingId(),
                entity.getBookingCode(),
                entity.getCustomerId(),
                entity.getVehicleId(),
                entity.getServiceOfferingId(),
                entity.getLocationId(),
                entity.getBookingDate(),
                entity.getBookingTime(),
                BookingStatus.valueOf(entity.getStatus()),
                entity.getTotalAmount(),
                entity.getNotes()
        );
    }

    // convierte de modelo de dominio a entidad JPA
    private BookingJpaEntity toEntity(Booking booking) {
        BookingJpaEntity entity = new BookingJpaEntity();
        entity.setBookingId(booking.bookingId());
        entity.setBookingCode(booking.bookingCode());
        entity.setCustomerId(booking.customerId());
        entity.setVehicleId(booking.vehicleId());
        entity.setServiceOfferingId(booking.serviceOfferingId());
        entity.setLocationId(booking.locationId());
        entity.setBookingDate(booking.bookingDate());
        entity.setBookingTime(booking.bookingTime());
        entity.setStatus(booking.status().name());
        entity.setTotalAmount(booking.totalAmount());
        entity.setNotes(booking.notes());
        return entity;
    }
}
