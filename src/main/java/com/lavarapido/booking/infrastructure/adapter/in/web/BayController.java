package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.Bay;
import com.lavarapido.booking.domain.port.out.BayRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controlador REST para las bahías de trabajo
@RestController
@RequestMapping("/api/v1/bays")
public class BayController {

    private final BayRepository bayRepository;

    public BayController(BayRepository bayRepository) {
        this.bayRepository = bayRepository;
    }

    // listar bahías de una sede
    @GetMapping("/location/{locationId}")
    public ResponseEntity<List<Bay>> listByLocation(@PathVariable Long locationId) {
        List<Bay> bays = bayRepository.findByLocationId(locationId);
        return ResponseEntity.ok(bays);
    }
}
