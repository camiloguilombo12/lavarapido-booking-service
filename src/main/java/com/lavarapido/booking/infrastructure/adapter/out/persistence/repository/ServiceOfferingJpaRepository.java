package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// repositorio JPA para la tabla service_offering
@Repository
public interface ServiceOfferingJpaRepository extends JpaRepository<ServiceOfferingJpaEntity, Long> {

    List<ServiceOfferingJpaEntity> findByIsActiveTrue();
}
