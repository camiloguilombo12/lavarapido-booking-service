package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.Schedule;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ScheduleJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ScheduleJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

// adaptador que implementa el puerto ScheduleRepository usando JPA
@Component
public class SchedulePersistenceAdapter implements ScheduleRepository {

    private final ScheduleJpaRepository jpaRepository;

    public SchedulePersistenceAdapter(ScheduleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Schedule> findByLocationId(Long locationId) {
        return jpaRepository.findByLocationIdAndIsActiveTrue(locationId)
                .stream().map(this::toDomain).toList();
    }

    // convierte de entidad JPA a modelo de dominio
    private Schedule toDomain(ScheduleJpaEntity entity) {
        return new Schedule(
                entity.getScheduleId(),
                entity.getLocationId(),
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getSlotDurationMinutes(),
                entity.getIsActive()
        );
    }
}
