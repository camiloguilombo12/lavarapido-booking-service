package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.exception.NotFoundException;
import com.lavarapido.booking.domain.model.CatalogItem;
import com.lavarapido.booking.domain.model.PriceDefinition;
import com.lavarapido.booking.domain.model.ServiceCategory;
import com.lavarapido.booking.domain.port.in.CatalogUseCase;
import com.lavarapido.booking.domain.port.in.ServiceCommand;
import com.lavarapido.booking.domain.port.out.CatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Catalogo de servicios. Los precios nunca se editan en su lugar: cada cambio cierra la tarifa
 * vigente y crea otra (INV-BOOK-008), eso lo hace el repositorio en replacePrices.
 */
@Service
@Transactional
public class CatalogService implements CatalogUseCase {

    private final CatalogRepository catalog;
    private final BookingSettings settings;
    private final Clock clock;

    public CatalogService(CatalogRepository catalog, BookingSettings settings, Clock clock) {
        this.catalog = catalog;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceCategory> categories() {
        return catalog.findCategories();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogItem> services(boolean includeInactive, Short vehicleTypeId) {
        List<CatalogItem> items = catalog.findServices(includeInactive);
        if (vehicleTypeId == null) {
            return items;
        }
        return items.stream()
                .filter(item -> item.priceFor(vehicleTypeId).isPresent())
                .map(item -> new CatalogItem(item.id(), item.code(), item.name(), item.description(),
                        item.category(), item.active(), List.of(item.priceFor(vehicleTypeId).orElseThrow())))
                .toList();
    }

    @Override
    public CatalogItem create(ServiceCommand command, long actor) {
        String name = CatalogItem.requireName(command.name());
        String code = CatalogItem.normalizeCode(command.code() == null || command.code().isBlank()
                ? codeFromName(name) : command.code());
        if (catalog.existsCode(code)) {
            throw new ConflictException("SERVICE_CODE_TAKEN", "Another service already uses code " + code);
        }
        requireCategory(command.categoryId());
        List<PriceDefinition> prices = requirePrices(command.prices());

        int serviceId = catalog.insertService(code, name, cleanDescription(command.description()),
                command.categoryId(), actor);
        catalog.replacePrices(serviceId, prices, today(), actor, now());
        return requireService(serviceId);
    }

    @Override
    public CatalogItem update(int serviceId, ServiceCommand command, long actor) {
        requireService(serviceId);
        String name = CatalogItem.requireName(command.name());
        requireCategory(command.categoryId());
        catalog.updateService(serviceId, name, cleanDescription(command.description()), command.categoryId(), actor);
        if (command.prices() != null && !command.prices().isEmpty()) {
            catalog.replacePrices(serviceId, requirePrices(command.prices()), today(), actor, now());
        }
        return requireService(serviceId);
    }

    @Override
    public CatalogItem setActive(int serviceId, boolean active, long actor) {
        requireService(serviceId);
        catalog.setActive(serviceId, active, actor);
        return requireService(serviceId);
    }

    /** Borrado logico: las reservas que ya lo usan conservan su tarifa. */
    @Override
    public void delete(int serviceId, long actor) {
        requireService(serviceId);
        catalog.softDelete(serviceId, actor, now());
    }

    private CatalogItem requireService(int serviceId) {
        return catalog.findService(serviceId)
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND", "Service " + serviceId + " does not exist"));
    }

    private void requireCategory(short categoryId) {
        if (catalog.findCategory(categoryId).isEmpty()) {
            throw new InvalidValueException("INVALID_CATEGORY", "Category " + categoryId + " does not exist");
        }
    }

    private static List<PriceDefinition> requirePrices(List<PriceDefinition> prices) {
        if (prices == null || prices.isEmpty()) {
            throw new InvalidValueException("PRICES_REQUIRED", "A service needs a price for at least one vehicle type");
        }
        Set<Short> seen = new HashSet<>();
        for (PriceDefinition price : prices) {
            if (!seen.add(price.vehicleTypeId())) {
                throw new InvalidValueException("DUPLICATED_VEHICLE_TYPE", "Each vehicle type can have only one price");
            }
        }
        return prices;
    }

    private static String cleanDescription(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /** "Lavado de motor" -> "LAVADO_DE_MOTOR" (sin tildes), cortado a 30 caracteres. */
    static String codeFromName(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = plain.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        return code.length() > 30 ? code.substring(0, 30) : code;
    }

    private Instant now() {
        return clock.instant();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }
}
