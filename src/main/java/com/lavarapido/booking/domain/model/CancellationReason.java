package com.lavarapido.booking.domain.model;

import java.util.Set;

/** Motivo de cancelacion. customerFault queda para reglas futuras (penalizaciones). */
public record CancellationReason(short id, String code, String name, boolean customerFault) {

    /** Motivo con el que el sistema cancela una programada que vencio: nadie lo elige a mano. */
    public static final String EXPIRED = "EXPIRED";

    // motivos del lado del cliente; los del lavadero (SHOP_ISSUE, LATE_ARRIVAL) los pone el administrador
    private static final Set<String> CUSTOMER_SELECTABLE =
            Set.of("CUSTOMER_REQUEST", "SCHEDULE_CONFLICT", "VEHICLE_ISSUE", "OTHER");

    /** A-20: el administrador elige cualquiera menos el del sistema; el cliente, solo los de su lado. */
    public boolean selectableBy(boolean admin) {
        if (EXPIRED.equals(code)) {
            return false;
        }
        return admin || CUSTOMER_SELECTABLE.contains(code);
    }
}
