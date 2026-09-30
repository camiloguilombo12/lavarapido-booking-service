package com.lavarapido.booking.infrastructure.adapter.in.web.dto;

import com.lavarapido.booking.domain.model.CatalogItem;
import com.lavarapido.booking.domain.model.PriceDefinition;
import com.lavarapido.booking.domain.model.ServiceCategory;
import com.lavarapido.booking.domain.model.ServicePrice;
import com.lavarapido.booking.domain.port.in.ServiceCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contratos HTTP del catalogo (/api/v1/catalog y /api/v1/admin/catalog). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryResponse(short id, String code, String name) {

        public static CategoryResponse from(ServiceCategory category) {
            return category == null ? null : new CategoryResponse(category.id(), category.code(), category.name());
        }
    }

    /** Tarifa vigente de un tipo de vehiculo (vehicleTypeId es el de customer-service). */
    public record PriceResponse(short vehicleTypeId, BigDecimal price, short estimatedMinutes, LocalDate validFrom) {

        static PriceResponse from(ServicePrice price) {
            return new PriceResponse(price.vehicleTypeId(), price.price(), price.estimatedMinutes(), price.validFrom());
        }
    }

    public record ServiceResponse(int id, String code, String name, String description, CategoryResponse category,
                                  boolean active, List<PriceResponse> prices) {

        public static ServiceResponse from(CatalogItem item) {
            return new ServiceResponse(item.id(), item.code(), item.name(), item.description(),
                    CategoryResponse.from(item.category()), item.active(),
                    item.prices().stream().map(PriceResponse::from).toList());
        }
    }

    public record PriceRequest(
            @NotNull Short vehicleTypeId,
            @NotNull BigDecimal price,
            @NotNull Short estimatedMinutes) {

        PriceDefinition toDefinition() {
            return new PriceDefinition(vehicleTypeId, price, estimatedMinutes);
        }
    }

    /**
     * Alta o edicion. code es opcional (si no llega se arma con el nombre). Al editar, prices
     * vacio deja las tarifas como estan.
     */
    public record ServiceRequest(
            @Size(max = 30) String code,
            @NotBlank @Size(max = 100) String name,
            String description,
            @NotNull Short categoryId,
            @Valid List<PriceRequest> prices) {

        public ServiceCommand toCommand() {
            List<PriceDefinition> definitions = prices == null ? List.of()
                    : prices.stream().map(PriceRequest::toDefinition).toList();
            return new ServiceCommand(code, name, description, categoryId, definitions);
        }
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }
}
