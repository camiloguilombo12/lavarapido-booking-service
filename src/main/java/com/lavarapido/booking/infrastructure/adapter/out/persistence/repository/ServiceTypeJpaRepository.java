package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceTypeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// repositorio JPA para la tabla service_type
@Repository
public interface ServiceTypeJpaRepository extends JpaRepository<ServiceTypeJpaEntity, Long> {

    List<ServiceTypeJpaEntity> findByIsActiveTrue();
}
