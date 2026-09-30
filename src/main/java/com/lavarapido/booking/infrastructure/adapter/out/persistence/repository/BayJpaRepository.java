package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BayJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// repositorio JPA para la tabla bay
@Repository
public interface BayJpaRepository extends JpaRepository<BayJpaEntity, Long> {

    List<BayJpaEntity> findByLocationIdAndIsActiveTrue(Long locationId);
}
