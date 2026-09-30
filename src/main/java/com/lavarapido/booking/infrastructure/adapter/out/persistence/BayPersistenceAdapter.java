package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.Bay;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BayJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.BayJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

// adaptador que implementa el puerto BayRepository usando JPA
@Component
public class BayPersistenceAdapter implements BayRepository {

    private final BayJpaRepository jpaRepository;

    public BayPersistenceAdapter(BayJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Bay> findByLocationId(Long locationId) {
        return jpaRepository.findByLocationIdAndIsActiveTrue(locationId)
                .stream().map(this::toDomain).toList();
    }

    // convierte de entidad JPA a modelo de dominio
    private Bay toDomain(BayJpaEntity entity) {
        return new Bay(
                entity.getBayId(),
                entity.getLocationId(),
                entity.getName(),
                entity.getIsActive()
        );
    }
}
