package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.Payment;
import com.lavarapido.booking.domain.port.out.PaymentRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.PaymentJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.PaymentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

// adaptador que implementa el puerto PaymentRepository usando JPA
@Component
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentPersistenceAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Payment> findByBookingId(Long bookingId) {
        return jpaRepository.findByBookingId(bookingId)
                .stream().map(this::toDomain).toList();
    }

    // convierte de entidad JPA a modelo de dominio
    private Payment toDomain(PaymentJpaEntity entity) {
        return new Payment(
                entity.getPaymentId(),
                entity.getBookingId(),
                entity.getAmount(),
                entity.getPaymentMethod(),
                entity.getStatus(),
                entity.getTransactionRef(),
                entity.getPaidAt()
        );
    }
}
