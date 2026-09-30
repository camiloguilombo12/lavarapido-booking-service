package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.ServiceOffering;
import com.lavarapido.booking.domain.port.out.ServiceOfferingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controlador REST para las ofertas de servicio
@RestController
@RequestMapping("/api/v1/service-offerings")
public class ServiceOfferingController {

    private final ServiceOfferingRepository serviceOfferingRepository;

    public ServiceOfferingController(ServiceOfferingRepository serviceOfferingRepository) {
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    // listar todas las ofertas activas
    @GetMapping
    public ResponseEntity<List<ServiceOffering>> listAll() {
        List<ServiceOffering> offerings = serviceOfferingRepository.findAllActive();
        return ResponseEntity.ok(offerings);
    }

    // obtener una oferta por su id
    @GetMapping("/{offeringId}")
    public ResponseEntity<ServiceOffering> getById(@PathVariable Long offeringId) {
        return serviceOfferingRepository.findById(offeringId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
