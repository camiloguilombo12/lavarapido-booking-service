package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.CatalogItem;
import com.lavarapido.booking.domain.model.PriceDefinition;
import com.lavarapido.booking.domain.model.ServiceCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Catalogo de servicios y sus tarifas (esquema catalog). Las tarifas que devuelve son las vigentes. */
public interface CatalogRepository {

    List<ServiceCategory> findCategories();

    Optional<ServiceCategory> findCategory(short categoryId);

    List<CatalogItem> findServices(boolean includeInactive);

    List<CatalogItem> findServicesByIds(List<Integer> serviceIds);

    Optional<CatalogItem> findService(int serviceId);

    boolean existsCode(String code);

    /** Crea el servicio (sin tarifas) y devuelve su id. */
    int insertService(String code, String name, String description, short categoryId, long actor);

    void updateService(int serviceId, String name, String description, short categoryId, long actor);

    void setActive(int serviceId, boolean active, long actor);

    /** Borrado logico del servicio y de sus tarifas vigentes. */
    void softDelete(int serviceId, long actor, Instant now);

    /**
     * Deja vigentes estas tarifas desde today. La que ya este igual no se toca; la que cambia se
     * cierra (valid_to) y se inserta una nueva, porque service_price es inmutable.
     */
    void replacePrices(int serviceId, List<PriceDefinition> prices, LocalDate today, long actor, Instant now);
}
