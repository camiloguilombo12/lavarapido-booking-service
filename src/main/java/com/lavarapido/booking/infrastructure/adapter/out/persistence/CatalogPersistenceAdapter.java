package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.CatalogItem;
import com.lavarapido.booking.domain.model.PriceDefinition;
import com.lavarapido.booking.domain.model.ServiceCategory;
import com.lavarapido.booking.domain.model.ServicePrice;
import com.lavarapido.booking.domain.port.out.CatalogRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceCategoryJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServicePriceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServiceCategoryJpaRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServiceJpaRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServicePriceJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Servicios, categorias y tarifas vigentes (esquema catalog). */
@Repository
class CatalogPersistenceAdapter implements CatalogRepository {

    private final ServiceCategoryJpaRepository categories;
    private final ServiceJpaRepository services;
    private final ServicePriceJpaRepository prices;

    CatalogPersistenceAdapter(ServiceCategoryJpaRepository categories, ServiceJpaRepository services,
                              ServicePriceJpaRepository prices) {
        this.categories = categories;
        this.services = services;
        this.prices = prices;
    }

    @Override
    public List<ServiceCategory> findCategories() {
        return categories.findByActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc().stream()
                .map(CatalogPersistenceAdapter::toCategory)
                .toList();
    }

    @Override
    public Optional<ServiceCategory> findCategory(short categoryId) {
        return categories.findById(categoryId)
                .filter(category -> category.getDeletedAt() == null)
                .map(CatalogPersistenceAdapter::toCategory);
    }

    @Override
    public List<CatalogItem> findServices(boolean includeInactive) {
        return toItems(includeInactive
                ? services.findByDeletedAtIsNullOrderByNameAsc()
                : services.findByActiveTrueAndDeletedAtIsNullOrderByNameAsc());
    }

    @Override
    public List<CatalogItem> findServicesByIds(List<Integer> serviceIds) {
        return toItems(services.findByIdInAndDeletedAtIsNull(serviceIds));
    }

    @Override
    public Optional<CatalogItem> findService(int serviceId) {
        return services.findByIdAndDeletedAtIsNull(serviceId).map(entity -> toItems(List.of(entity)).getFirst());
    }

    @Override
    public boolean existsCode(String code) {
        return services.existsByCode(code);
    }

    @Override
    public int insertService(String code, String name, String description, short categoryId, int loyaltyPoints,
                             long actor) {
        ServiceJpaEntity entity = new ServiceJpaEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        entity.setCategoryId(categoryId);
        entity.setLoyaltyPoints(loyaltyPoints);
        entity.setActive(true);
        entity.setCreatedBy(actor);
        return services.save(entity).getId();
    }

    @Override
    public void updateService(int serviceId, String name, String description, short categoryId, int loyaltyPoints,
                              long actor) {
        ServiceJpaEntity entity = services.findById(serviceId).orElseThrow();
        entity.setName(name);
        entity.setDescription(description);
        entity.setCategoryId(categoryId);
        entity.setLoyaltyPoints(loyaltyPoints);
        entity.setUpdatedBy(actor);
        services.save(entity);
    }

    @Override
    public void setActive(int serviceId, boolean active, long actor) {
        ServiceJpaEntity entity = services.findById(serviceId).orElseThrow();
        entity.setActive(active);
        entity.setUpdatedBy(actor);
        services.save(entity);
    }

    @Override
    public void softDelete(int serviceId, long actor, Instant now) {
        ServiceJpaEntity entity = services.findById(serviceId).orElseThrow();
        entity.setDeletedAt(now);
        entity.setDeletedBy(actor);
        services.save(entity);
        for (ServicePriceJpaEntity price : prices.findByServiceIdInAndValidToIsNullAndDeletedAtIsNull(List.of(serviceId))) {
            price.setDeletedAt(now);
            price.setDeletedBy(actor);
            prices.save(price);
        }
    }

    /**
     * service_price es inmutable: una tarifa que cambia se cierra y se inserta otra.
     * Cerrar exige valid_to > valid_from, asi que una tarifa creada hoy mismo no se puede cerrar
     * hoy; en ese caso se borra logicamente (nadie la alcanzo a ver como historica) y se inserta
     * la nueva. Los tipos que ya no vienen en la lista se cierran igual.
     */
    @Override
    public void replacePrices(int serviceId, List<PriceDefinition> definitions, LocalDate today, long actor, Instant now) {
        Map<Short, ServicePriceJpaEntity> current = prices
                .findByServiceIdInAndValidToIsNullAndDeletedAtIsNull(List.of(serviceId)).stream()
                .collect(Collectors.toMap(ServicePriceJpaEntity::getVehicleTypeId, Function.identity()));
        Map<Short, PriceDefinition> wanted = definitions.stream()
                .collect(Collectors.toMap(PriceDefinition::vehicleTypeId, Function.identity()));

        for (ServicePriceJpaEntity existing : current.values()) {
            PriceDefinition next = wanted.get(existing.getVehicleTypeId());
            if (next == null || !next.sameAs(toPrice(existing))) {
                retire(existing, today, actor, now);
            }
        }
        prices.flush();
        for (PriceDefinition definition : definitions) {
            ServicePriceJpaEntity existing = current.get(definition.vehicleTypeId());
            if (existing != null && definition.sameAs(toPrice(existing))) {
                continue;
            }
            ServicePriceJpaEntity created = new ServicePriceJpaEntity();
            created.setServiceId(serviceId);
            created.setVehicleTypeId(definition.vehicleTypeId());
            created.setPrice(definition.price());
            created.setEstimatedMinutes(definition.estimatedMinutes());
            created.setValidFrom(today);
            created.setCreatedBy(actor);
            prices.save(created);
        }
    }

    private void retire(ServicePriceJpaEntity price, LocalDate today, long actor, Instant now) {
        if (price.getValidFrom().isBefore(today)) {
            price.setValidTo(today);
        } else {
            price.setDeletedAt(now);
            price.setDeletedBy(actor);
        }
        price.setUpdatedBy(actor);
        prices.save(price);
    }

    private List<CatalogItem> toItems(Collection<ServiceJpaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Short, ServiceCategory> categoryById = categories.findAll().stream()
                .collect(Collectors.toMap(ServiceCategoryJpaEntity::getId, CatalogPersistenceAdapter::toCategory));
        Map<Integer, List<ServicePrice>> pricesByService = prices
                .findByServiceIdInAndValidToIsNullAndDeletedAtIsNull(
                        entities.stream().map(ServiceJpaEntity::getId).toList()).stream()
                .map(CatalogPersistenceAdapter::toPrice)
                .sorted(Comparator.comparing(ServicePrice::vehicleTypeId))
                .collect(Collectors.groupingBy(ServicePrice::serviceId));
        return entities.stream()
                .map(entity -> new CatalogItem(entity.getId(), entity.getCode(), entity.getName(),
                        entity.getDescription(), categoryById.get(entity.getCategoryId()),
                        Boolean.TRUE.equals(entity.getActive()),
                        pricesByService.getOrDefault(entity.getId(), List.of()),
                        entity.getLoyaltyPoints() == null ? 0 : entity.getLoyaltyPoints()))
                .toList();
    }

    static ServicePrice toPrice(ServicePriceJpaEntity entity) {
        return new ServicePrice(entity.getId(), entity.getServiceId(), entity.getVehicleTypeId(), entity.getPrice(),
                entity.getEstimatedMinutes(), entity.getValidFrom(), entity.getValidTo());
    }

    private static ServiceCategory toCategory(ServiceCategoryJpaEntity entity) {
        return new ServiceCategory(entity.getId(), entity.getCode(), entity.getName(), entity.getDisplayOrder());
    }
}
