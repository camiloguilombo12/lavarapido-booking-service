package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.port.in.CatalogUseCase;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.CatalogDtos.ActiveRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.CatalogDtos.CategoryResponse;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.CatalogDtos.ServiceRequest;
import com.lavarapido.booking.infrastructure.adapter.in.web.dto.CatalogDtos.ServiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de servicios.
 * Publico: /api/v1/catalog/** (la landing y el formulario de reserva lo leen).
 * ADMIN: /api/v1/admin/catalog/** (pestaña "Servicios" de Gestion).
 */
@RestController
class CatalogController {

    private final CatalogUseCase catalog;

    CatalogController(CatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/catalog/categories")
    List<CategoryResponse> categories() {
        return catalog.categories().stream().map(CategoryResponse::from).toList();
    }

    /** Servicios activos. Con vehicleTypeId trae solo el precio de ese tipo de vehiculo. */
    @GetMapping("/api/v1/catalog/services")
    List<ServiceResponse> services(@RequestParam(required = false) Short vehicleTypeId) {
        return catalog.services(false, vehicleTypeId).stream().map(ServiceResponse::from).toList();
    }

    @GetMapping("/api/v1/admin/catalog/services")
    List<ServiceResponse> allServices() {
        return catalog.services(true, null).stream().map(ServiceResponse::from).toList();
    }

    @PostMapping("/api/v1/admin/catalog/services")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ServiceRequest request) {
        return ServiceResponse.from(catalog.create(request.toCommand(), AuthenticatedUser.userId(jwt)));
    }

    @PutMapping("/api/v1/admin/catalog/services/{id}")
    ServiceResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                           @Valid @RequestBody ServiceRequest request) {
        return ServiceResponse.from(catalog.update(id, request.toCommand(), AuthenticatedUser.userId(jwt)));
    }

    @PatchMapping("/api/v1/admin/catalog/services/{id}/status")
    ServiceResponse setActive(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                              @Valid @RequestBody ActiveRequest request) {
        return ServiceResponse.from(catalog.setActive(id, request.active(), AuthenticatedUser.userId(jwt)));
    }

    @DeleteMapping("/api/v1/admin/catalog/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        catalog.delete(id, AuthenticatedUser.userId(jwt));
    }
}
