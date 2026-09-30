package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.ServiceOffering;
import com.lavarapido.booking.domain.port.out.ServiceOfferingRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServiceOfferingJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// adaptador que implementa el puerto ServiceOfferingRepository usando JPA
@Component
public class ServiceOfferingPersistenceAdapter implements ServiceOfferingRepository {

    private final ServiceOfferingJpaRepository jpaRepository;

    public ServiceOfferingPersistenceAdapter(ServiceOfferingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ServiceOffering> findAllActive() {
        return jpaRepository.findByIsActiveTrue()
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ServiceOffering> findById(Long serviceOfferingId) {
        return jpaRepository.findById(serviceOfferingId).map(this::toDomain);
    }

    // convierte de entidad JPA a modelo de dominio
    private ServiceOffering toDomain(ServiceOfferingJpaEntity entity) {
        return new ServiceOffering(
                entity.getServiceOfferingId(),
                entity.getServiceTypeId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getIsActive()
        );
    }
}
