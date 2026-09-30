package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// repositorio JPA para la tabla booking
@Repository
public interface BookingJpaRepository extends JpaRepository<BookingJpaEntity, Long> {

    Optional<BookingJpaEntity> findByBookingCode(String bookingCode);

    List<BookingJpaEntity> findByCustomerIdAndDeletedAtIsNull(Long customerId);

    List<BookingJpaEntity> findByStatusAndDeletedAtIsNull(String status);

    boolean existsByBookingCode(String bookingCode);
}
