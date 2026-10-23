package com.lavarapido.booking.domain.model;

/**
 * Por que la hora pedida no sirve (RF-006). Va en el 409 SLOT_UNAVAILABLE como "reason" para que
 * la pantalla no diga "no hay bahia libre" cuando en realidad el lavadero esta cerrado a esa hora.
 */
public enum UnavailableReason {
    /** El lavadero no abre ese dia (horario semanal o excepcion). */
    CLOSED_DAY,
    /** El servicio no cabe en el horario del dia a esa hora: abre despues, cierra antes o cruza la pausa. */
    OUTSIDE_HOURS,
    /** La hora ya paso (hoy, con la hora del lavadero). */
    TIME_PASSED,
    /** El horario sirve, pero todas las bahias activas estan ocupadas. */
    NO_FREE_BAY
}
