package com.lavarapido.booking.domain.exception;

import java.time.LocalTime;
import java.util.List;

/**
 * RF-006: la hora pedida no tiene bahia libre. No se guarda nada y se devuelven hasta N horas
 * libres del mismo dia para que el cliente escoja otra.
 */
public class SlotUnavailableException extends ConflictException {

    private final List<LocalTime> alternatives;

    public SlotUnavailableException(List<LocalTime> alternatives) {
        super("SLOT_UNAVAILABLE", "No service bay is free at the requested time");
        this.alternatives = List.copyOf(alternatives);
    }

    public List<LocalTime> alternatives() {
        return alternatives;
    }
}
