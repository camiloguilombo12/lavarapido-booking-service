package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceJpaRepository extends JpaRepository<ServiceJpaEntity, Integer> {

    List<ServiceJpaEntity> findByDeletedAtIsNullOrderByNameAsc();

    List<ServiceJpaEntity> findByActiveTrueAndDeletedAtIsNullOrderByNameAsc();

    List<ServiceJpaEntity> findByIdInAndDeletedAtIsNull(Collection<Integer> ids);

    Optional<ServiceJpaEntity> findByIdAndDeletedAtIsNull(int id);

    /** Incluye los borrados: uq_service_code no es filtrado. */
    boolean existsByCode(String code);
}
