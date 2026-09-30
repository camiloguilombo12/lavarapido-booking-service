package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceBayJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceBayJpaRepository extends JpaRepository<ServiceBayJpaEntity, Short> {

    List<ServiceBayJpaEntity> findByDeletedAtIsNullOrderByIdAsc();

    Optional<ServiceBayJpaEntity> findByIdAndDeletedAtIsNull(short id);

    List<ServiceBayJpaEntity> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    /** Incluye las borradas: uq_service_bay_code no es filtrado. */
    boolean existsByCode(String code);
}
