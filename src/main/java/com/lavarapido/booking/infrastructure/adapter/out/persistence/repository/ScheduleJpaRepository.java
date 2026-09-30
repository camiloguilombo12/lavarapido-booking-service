package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ScheduleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// repositorio JPA para la tabla schedule
@Repository
public interface ScheduleJpaRepository extends JpaRepository<ScheduleJpaEntity, Long> {

    List<ScheduleJpaEntity> findByLocationIdAndIsActiveTrue(Long locationId);
}
