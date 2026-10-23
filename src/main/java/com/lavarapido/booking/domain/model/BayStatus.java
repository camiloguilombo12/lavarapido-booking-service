package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.util.Arrays;
import java.util.Locale;

/** Estado de una bahia (ADR-010). Solo ACTIVE recibe reservas nuevas. Ids fijos de la migracion 018. */
public enum BayStatus {

    ACTIVE(1),
    MAINTENANCE(2),
    INACTIVE(3);

    private final short id;

    BayStatus(int id) {
        this.id = (short) id;
    }

    public short id() {
        return id;
    }

    public boolean acceptsBookings() {
        return this == ACTIVE;
    }

    public static BayStatus ofId(short id) {
        return Arrays.stream(values())
                .filter(status -> status.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown bay status id " + id));
    }

    public static BayStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidValueException("INVALID_BAY_STATUS", "Bay status is required");
        }
        String candidate = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.name().equals(candidate))
                .findFirst()
                .orElseThrow(() -> new InvalidValueException("INVALID_BAY_STATUS", "Unknown bay status " + raw));
    }
}
