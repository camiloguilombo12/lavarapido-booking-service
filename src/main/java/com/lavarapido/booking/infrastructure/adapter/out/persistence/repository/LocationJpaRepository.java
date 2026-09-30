package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.LocationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// repositorio JPA para la tabla location
@Repository
public interface LocationJpaRepository extends JpaRepository<LocationJpaEntity, Long> {

    List<LocationJpaEntity> findByIsActiveTrue();
}
