package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;

// tipo de servicio con su precio base y duración
public record ServiceType(
    Long serviceTypeCode,
    String code,
    String name,
    String description,
    BigDecimal basePrice,
    Integer durationMinutes,
    boolean isActive
) {}
