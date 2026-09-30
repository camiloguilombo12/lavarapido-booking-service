package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.Schedule;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controlador REST para los horarios de atención
@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;

    public ScheduleController(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // listar horarios de una sede
    @GetMapping("/location/{locationId}")
    public ResponseEntity<List<Schedule>> listByLocation(@PathVariable Long locationId) {
        List<Schedule> schedules = scheduleRepository.findByLocationId(locationId);
        return ResponseEntity.ok(schedules);
    }
}
