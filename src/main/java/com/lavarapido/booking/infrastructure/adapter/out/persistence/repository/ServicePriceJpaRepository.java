package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServicePriceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServicePriceJpaRepository extends JpaRepository<ServicePriceJpaEntity, Long> {

    /** Las tarifas vigentes (sin cerrar y sin borrar) de esos servicios: una por tipo de vehiculo. */
    List<ServicePriceJpaEntity> findByServiceIdInAndValidToIsNullAndDeletedAtIsNull(Collection<Integer> serviceIds);
}
