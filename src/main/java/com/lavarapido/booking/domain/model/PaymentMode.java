package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Cómo va a pagar el cliente la reserva (contrato de pago de la reserva, 26-10-2026). Se guarda
 * en booking.payment_mode con el mismo nombre (CHECK ck_booking_payment_mode).
 */
public enum PaymentMode {

    /** Nequi, Daviplata o transferencia: QR y comprobante que verifica el admin. Tiene plazo. */
    DIGITAL,
    /** Efectivo en el lavadero: lo registra el admin, sin comprobante. Tiene límite de reservas activas. */
    CASH;

    /** El cliente siempre lo manda: si falta o no es DIGITAL ni CASH, 400 INVALID_PAYMENT_MODE. */
    public static PaymentMode of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Choose how the booking will be paid (DIGITAL or CASH)");
        }
        String candidate = raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.name().equals(candidate))
                .findFirst()
                .orElseThrow(() -> invalid("Unknown payment mode " + raw));
    }

    /** Para el admin es opcional: sin valor queda null; uno que no existe también es 400. */
    public static PaymentMode ofOptional(String raw) {
        return raw == null || raw.isBlank() ? null : of(raw);
    }

    /** Lo que viene de la base; una fila vieja (o un valor raro) queda sin modo. */
    public static PaymentMode fromColumn(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values()).filter(mode -> mode.name().equals(value)).findFirst().orElse(null);
    }

    private static InvalidValueException invalid(String message) {
        return new InvalidValueException("INVALID_PAYMENT_MODE", message);
    }
}
