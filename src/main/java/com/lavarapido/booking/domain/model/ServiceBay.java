package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

/**
 * Una bahia del lavadero. El numero de bahias activas es la capacidad del negocio.
 *
 * <p>El operario NO se asigna a la bahia de forma fija (ADR-010): la bahia es por reserva
 * (booking.service_bay_id) y el operario por ejecucion (execution.operator_id, operation-service).
 * Quien esta en cual bahia ahora se calcula, no se guarda aqui (ver ScheduleUseCase.bayOccupancy).
 */
public record ServiceBay(short id, String code, String name, BayStatus status) {

    public static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > 60) {
            throw new InvalidValueException("INVALID_BAY_NAME", "Bay name must have 1 to 60 characters");
        }
        return name;
    }

    /**
     * Estado con el que nace una bahia: activa (por defecto) o inactiva. Mantenimiento solo aplica
     * a una bahia que ya existe, por eso no se acepta al crearla.
     */
    public static BayStatus requireInitialStatus(BayStatus status) {
        if (status == null) {
            return BayStatus.ACTIVE;
        }
        if (status == BayStatus.MAINTENANCE) {
            throw new InvalidValueException("BAY_NEW_IN_MAINTENANCE",
                    "A new bay cannot start in maintenance; create it and then change its status");
        }
        return status;
    }
}
