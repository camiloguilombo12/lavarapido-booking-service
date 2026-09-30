package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.PaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// repositorio JPA para la tabla payment
@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {

    List<PaymentJpaEntity> findByBookingId(Long bookingId);
}
