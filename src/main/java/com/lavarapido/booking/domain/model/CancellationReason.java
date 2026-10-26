package com.lavarapido.booking.domain.model;

import java.util.Set;

/** Motivo de cancelacion. customerFault queda para reglas futuras (penalizaciones). */
public record CancellationReason(short id, String code, String name, boolean customerFault) {

    /** Motivo con el que el sistema cancela una programada que vencio: nadie lo elige a mano. */
    public static final String EXPIRED = "EXPIRED";

    /** D: el sistema cancela una reserva DIGITAL que llego a su plazo sin comprobante (seed 117). */
    public static final String PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";

    // los que pone solo el sistema: no se ofrecen ni al cliente ni al admin
    private static final Set<String> SYSTEM_ONLY = Set.of(EXPIRED, PAYMENT_TIMEOUT);

    // motivos del lado del cliente; los del lavadero (SHOP_ISSUE, LATE_ARRIVAL) los pone el administrador
    private static final Set<String> CUSTOMER_SELECTABLE =
            Set.of("CUSTOMER_REQUEST", "SCHEDULE_CONFLICT", "VEHICLE_ISSUE", "OTHER");

    /** A-20: el administrador elige cualquiera menos los del sistema; el cliente, solo los de su lado. */
    public boolean selectableBy(boolean admin) {
        if (systemOnly()) {
            return false;
        }
        return admin || CUSTOMER_SELECTABLE.contains(code);
    }

    /** EXPIRED y PAYMENT_TIMEOUT: los pone el sistema, nadie los elige a mano. */
    public boolean systemOnly() {
        return SYSTEM_ONLY.contains(code);
    }
}
