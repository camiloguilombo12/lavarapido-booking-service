package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.ServiceType;

import java.util.List;

// caso de uso para listar los tipos de servicio disponibles
public interface ListServiceTypesUseCase {

    List<ServiceType> listActive();
}
