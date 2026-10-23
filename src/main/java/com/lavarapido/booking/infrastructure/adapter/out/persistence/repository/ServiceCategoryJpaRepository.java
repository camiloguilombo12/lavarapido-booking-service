package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceCategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceCategoryJpaRepository extends JpaRepository<ServiceCategoryJpaEntity, Short> {

    List<ServiceCategoryJpaEntity> findByActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc();
}
