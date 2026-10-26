package com.lavarapido.booking.domain.service;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.model.PaymentMode;

import java.time.Duration;
import java.time.Instant;

/**
 * Reglas del pago al reservar (contrato de pago de la reserva, 26-10-2026). Código puro, como
 * AvailabilityPolicy: BookingService le pasa los datos y aplica el resultado.
 *
 *  - B. Efectivo con límite: un cliente tiene como máximo N reservas activas en efectivo.
 *  - D. Vencimiento del pago digital: plazo = min(creación + ventana, hora de inicio).
 *
 * Las reservas que crea el admin no pasan por aquí (ni plazo ni límite): lo decide el caso de uso.
 */
public final class PaymentPolicy {

    private PaymentPolicy() {
    }

    /**
     * Hasta cuándo el cliente puede subir el comprobante. Solo DIGITAL tiene plazo; si la reserva
     * empieza antes de que se acabe la ventana, el plazo es la hora de inicio.
     */
    public static Instant deadlineFor(PaymentMode mode, Instant createdAt, Instant scheduledStart, Duration window) {
        if (mode != PaymentMode.DIGITAL) {
            return null;
        }
        Instant byWindow = createdAt.plus(window);
        return byWindow.isBefore(scheduledStart) ? byWindow : scheduledStart;
    }

    /** 409 CASH_BOOKING_LIMIT si el cliente ya tiene el máximo de reservas activas en efectivo. */
    public static void requireCashAllowed(long activeCashBookings, int limit) {
        if (activeCashBookings >= limit) {
            throw new ConflictException("CASH_BOOKING_LIMIT", "The customer already has " + activeCashBookings
                    + " active cash booking(s); the limit is " + limit);
        }
    }
}
