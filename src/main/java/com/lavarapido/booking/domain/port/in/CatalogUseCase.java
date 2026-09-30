package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.CatalogItem;
import com.lavarapido.booking.domain.model.ServiceCategory;

import java.util.List;

/** Catalogo de servicios: lectura publica y administracion (ADMIN). */
public interface CatalogUseCase {

    List<ServiceCategory> categories();

    /**
     * Servicios con sus tarifas vigentes. Con vehicleTypeId solo trae ese precio y deja fuera los
     * servicios que no tienen tarifa para ese tipo (no se pueden reservar).
     */
    List<CatalogItem> services(boolean includeInactive, Short vehicleTypeId);

    CatalogItem create(ServiceCommand command, long actor);

    CatalogItem update(int serviceId, ServiceCommand command, long actor);

    CatalogItem setActive(int serviceId, boolean active, long actor);

    void delete(int serviceId, long actor);
}
