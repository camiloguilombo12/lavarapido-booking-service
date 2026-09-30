package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.Location;
import com.lavarapido.booking.domain.port.out.LocationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controlador REST para las sedes del lavadero
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationRepository locationRepository;

    public LocationController(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    // listar todas las sedes activas
    @GetMapping
    public ResponseEntity<List<Location>> listAll() {
        List<Location> locations = locationRepository.findAllActive();
        return ResponseEntity.ok(locations);
    }

    // obtener una sede por su id
    @GetMapping("/{locationId}")
    public ResponseEntity<Location> getById(@PathVariable Long locationId) {
        return locationRepository.findById(locationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
