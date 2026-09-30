package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.application.usecase.ListServiceTypesService;
import com.lavarapido.booking.domain.model.ServiceType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controlador REST para el catálogo de tipos de servicio
@RestController
@RequestMapping("/api/v1/service-types")
public class ServiceTypeController {

    private final ListServiceTypesService listServiceTypesService;

    public ServiceTypeController(ListServiceTypesService listServiceTypesService) {
        this.listServiceTypesService = listServiceTypesService;
    }

    // listar tipos de servicio activos
    @GetMapping
    public ResponseEntity<List<ServiceType>> listActive() {
        List<ServiceType> types = listServiceTypesService.listActive();
        return ResponseEntity.ok(types);
    }
}
