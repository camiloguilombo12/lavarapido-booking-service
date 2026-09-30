package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.Location;
import com.lavarapido.booking.domain.port.out.LocationRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.LocationJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.LocationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// adaptador que implementa el puerto LocationRepository usando JPA
@Component
public class LocationPersistenceAdapter implements LocationRepository {

    private final LocationJpaRepository jpaRepository;

    public LocationPersistenceAdapter(LocationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Location> findAllActive() {
        return jpaRepository.findByIsActiveTrue()
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Location> findById(Long locationId) {
        return jpaRepository.findById(locationId).map(this::toDomain);
    }

    // convierte de entidad JPA a modelo de dominio
    private Location toDomain(LocationJpaEntity entity) {
        return new Location(
                entity.getLocationId(),
                entity.getName(),
                entity.getAddress(),
                entity.getPhone(),
                entity.getIsActive()
        );
    }
}
