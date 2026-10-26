package com.lavarapido.booking.application.usecase;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Parametros de la agenda (application.yml, app.booking.*).
 *
 * @param zone            zona horaria del lavadero: el horario y las fechas que ve el cliente
 * @param slotMinutes     cada cuanto se ofrece una hora de inicio
 * @param maxDaysAhead    hasta cuantos dias hacia adelante se puede reservar
 * @param alternatives    cuantas horas alternativas se ofrecen si la pedida esta ocupada (RF-006)
 * @param expiryGrace     cuanto se espera despues de la hora de fin antes de cerrar una reserva que
 *                        nunca se inicio (un cliente que llega tarde todavia se puede atender)
 * @param cashActiveLimit B: cuantas reservas activas en efectivo puede tener un cliente a la vez
 * @param paymentWindow   D: cuanto tiene el cliente para subir el comprobante de una reserva DIGITAL
 */
public record BookingSettings(ZoneId zone, int slotMinutes, int maxDaysAhead, int alternatives, Duration expiryGrace,
                              int cashActiveLimit, Duration paymentWindow) {

    public static final int DEFAULT_CASH_ACTIVE_LIMIT = 1;
    public static final Duration DEFAULT_PAYMENT_WINDOW = Duration.ofMinutes(30);

    public BookingSettings(ZoneId zone, int slotMinutes, int maxDaysAhead, int alternatives, Duration expiryGrace) {
        this(zone, slotMinutes, maxDaysAhead, alternatives, expiryGrace, DEFAULT_CASH_ACTIVE_LIMIT,
                DEFAULT_PAYMENT_WINDOW);
    }

    public BookingSettings(ZoneId zone, int slotMinutes, int maxDaysAhead, int alternatives) {
        this(zone, slotMinutes, maxDaysAhead, alternatives, Duration.ofMinutes(60));
    }
}
