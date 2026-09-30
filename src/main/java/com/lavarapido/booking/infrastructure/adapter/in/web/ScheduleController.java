package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.port.in.ScheduleUseCase;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.ScheduleDtos.BayRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.ScheduleDtos.BayResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.ScheduleDtos.BusinessHourDto;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.ScheduleDtos.EstablishmentResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.ScheduleDtos.ExceptionRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.ScheduleDtos.ExceptionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Horario, excepciones, bahias y datos de la sede.
 * Publico: /api/v1/schedule/** y /api/v1/establishment (solo lectura).
 * ADMIN: /api/v1/admin/schedule/** y /api/v1/admin/bays ("Horarios y bahias").
 */
@RestController
@Validated
class ScheduleController {

    private final ScheduleUseCase schedule;

    ScheduleController(ScheduleUseCase schedule) {
        this.schedule = schedule;
    }

    @GetMapping("/api/v1/schedule/business-hours")
    List<BusinessHourDto> businessHours() {
        return schedule.businessHours().stream().map(BusinessHourDto::from).toList();
    }

    @GetMapping("/api/v1/schedule/exceptions")
    List<ExceptionResponse> exceptions(@RequestParam(required = false) LocalDate from) {
        return schedule.exceptions(from).stream().map(ExceptionResponse::from).toList();
    }

    @GetMapping("/api/v1/establishment")
    EstablishmentResponse establishment() {
        return EstablishmentResponse.from(schedule.establishment());
    }

    /** Los 7 dias de una vez: la pantalla guarda la semana completa con "Guardar cambios". */
    @PutMapping("/api/v1/admin/schedule/business-hours")
    List<BusinessHourDto> updateBusinessHours(@AuthenticationPrincipal Jwt jwt,
                                              @RequestBody @NotEmpty List<@Valid BusinessHourDto> week) {
        return schedule.updateBusinessHours(week.stream().map(BusinessHourDto::toDomain).toList(),
                        AuthenticatedUser.userId(jwt)).stream()
                .map(BusinessHourDto::from)
                .toList();
    }

    @PostMapping("/api/v1/admin/schedule/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    ExceptionResponse createException(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExceptionRequest request) {
        return ExceptionResponse.from(schedule.createException(request.toDomain(), AuthenticatedUser.userId(jwt)));
    }

    @PutMapping("/api/v1/admin/schedule/exceptions/{id}")
    ExceptionResponse updateException(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                      @Valid @RequestBody ExceptionRequest request) {
        return ExceptionResponse.from(schedule.updateException(id, request.toDomain(), AuthenticatedUser.userId(jwt)));
    }

    @DeleteMapping("/api/v1/admin/schedule/exceptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteException(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        schedule.deleteException(id, AuthenticatedUser.userId(jwt));
    }

    @GetMapping("/api/v1/admin/bays")
    List<BayResponse> bays() {
        return schedule.bays().stream().map(BayResponse::from).toList();
    }

    @PostMapping("/api/v1/admin/bays")
    @ResponseStatus(HttpStatus.CREATED)
    BayResponse createBay(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BayRequest request) {
        return BayResponse.from(schedule.createBay(request.name(), request.statusOrDefault(), AuthenticatedUser.userId(jwt)));
    }

    @PutMapping("/api/v1/admin/bays/{id}")
    BayResponse updateBay(@AuthenticationPrincipal Jwt jwt, @PathVariable short id,
                          @Valid @RequestBody BayRequest request) {
        return BayResponse.from(schedule.updateBay(id, request.name(), request.statusOrDefault(),
                AuthenticatedUser.userId(jwt)));
    }

    @DeleteMapping("/api/v1/admin/bays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBay(@AuthenticationPrincipal Jwt jwt, @PathVariable short id) {
        schedule.deleteBay(id, AuthenticatedUser.userId(jwt));
    }
}
