package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.ServiceType;

import java.util.List;
import java.util.Optional;

// puerto para consultar el catálogo de tipos de servicio
public interface ServiceTypeRepository {

    List<ServiceType> findAllActive();

    Optional<ServiceType> findByCode(String code);

    Optional<ServiceType> findById(Long serviceTypeId);
}
