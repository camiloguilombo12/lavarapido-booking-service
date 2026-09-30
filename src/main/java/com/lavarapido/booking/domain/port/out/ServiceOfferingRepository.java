package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.ServiceOffering;

import java.util.List;
import java.util.Optional;

// puerto para consultar las ofertas de servicio
public interface ServiceOfferingRepository {

    List<ServiceOffering> findAllActive();

    Optional<ServiceOffering> findById(Long serviceOfferingId);
}
