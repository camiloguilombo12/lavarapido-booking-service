package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Estados de la reserva (ADR-010, codigos en ingles). El id es el de booking.booking_status y
 * es fijo: el CHECK ck_booking_cancel usa 5 = CANCELLED.
 */
public enum BookingStatus {

    SCHEDULED(1),
    CONFIRMED(2),
    IN_PROGRESS(3),
    COMPLETED(4),
    CANCELLED(5),
    NO_SHOW(6);

    private final short id;

    BookingStatus(int id) {
        this.id = (short) id;
    }

    public short id() {
        return id;
    }

    /** Las que ocupan la bahia: una cancelada o terminada la deja libre. */
    public boolean occupiesBay() {
        return this == SCHEDULED || this == CONFIRMED || this == IN_PROGRESS;
    }

    /** RF-007: solo se modifica o cancela mientras el servicio no ha empezado. */
    public boolean isChangeable() {
        return this == SCHEDULED || this == CONFIRMED;
    }

    public static BookingStatus ofId(short id) {
        return Arrays.stream(values())
                .filter(status -> status.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown booking status id " + id));
    }

    public static BookingStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidValueException("INVALID_STATUS", "Status is required");
        }
        String candidate = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.name().equals(candidate))
                .findFirst()
                .orElseThrow(() -> new InvalidValueException("INVALID_STATUS", "Unknown booking status " + raw));
    }
}
