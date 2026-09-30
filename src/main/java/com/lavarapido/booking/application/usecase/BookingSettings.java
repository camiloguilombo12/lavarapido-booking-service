package com.lavarapido.booking.application.usecase;

import java.time.ZoneId;

/**
 * Parametros de la agenda (application.yml, app.booking.*).
 *
 * @param zone          zona horaria del lavadero: el horario y las fechas que ve el cliente
 * @param slotMinutes   cada cuanto se ofrece una hora de inicio
 * @param maxDaysAhead  hasta cuantos dias hacia adelante se puede reservar
 * @param alternatives  cuantas horas alternativas se ofrecen si la pedida esta ocupada (RF-006)
 */
public record BookingSettings(ZoneId zone, int slotMinutes, int maxDaysAhead, int alternatives) {
}
