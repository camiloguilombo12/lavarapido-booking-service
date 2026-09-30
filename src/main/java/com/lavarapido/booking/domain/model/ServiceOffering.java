package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;

// oferta de servicio específica (ej. "Lavado Premium Automóvil")
public record ServiceOffering(
    Long serviceOfferingId,
    Long serviceTypeId,
    String name,
    String description,
    BigDecimal price,
    boolean isActive
) {}
