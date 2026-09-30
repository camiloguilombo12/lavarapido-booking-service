package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.ServiceType;
import com.lavarapido.booking.domain.port.out.ServiceTypeRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceTypeJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServiceTypeJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// adaptador que implementa el puerto ServiceTypeRepository usando JPA
@Component
public class ServiceTypePersistenceAdapter implements ServiceTypeRepository {

    private final ServiceTypeJpaRepository jpaRepository;

    public ServiceTypePersistenceAdapter(ServiceTypeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ServiceType> findAllActive() {
        return jpaRepository.findByIsActiveTrue()
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ServiceType> findByCode(String code) {
        return jpaRepository.findAll().stream()
                .filter(e -> e.getCode().equals(code))
                .map(this::toDomain)
                .findFirst();
    }

    @Override
    public Optional<ServiceType> findById(Long serviceTypeId) {
        return jpaRepository.findById(serviceTypeId).map(this::toDomain);
    }

    // convierte de entidad JPA a modelo de dominio
    private ServiceType toDomain(ServiceTypeJpaEntity entity) {
        return new ServiceType(
                entity.getServiceTypeId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getBasePrice(),
                entity.getDurationMinutes(),
                entity.getIsActive()
        );
    }
}
