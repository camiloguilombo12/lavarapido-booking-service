package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.model.ServiceType;
import com.lavarapido.booking.domain.port.in.ListServiceTypesUseCase;
import com.lavarapido.booking.domain.port.out.ServiceTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// implementación del caso de uso para listar tipos de servicio
@Service
public class ListServiceTypesService implements ListServiceTypesUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    public ListServiceTypesService(ServiceTypeRepository serviceTypeRepository) {
        this.serviceTypeRepository = serviceTypeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceType> listActive() {
        return serviceTypeRepository.findAllActive();
    }
}
