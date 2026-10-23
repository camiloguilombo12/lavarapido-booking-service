package com.lavarapido.booking.domain.exception;

import com.lavarapido.booking.domain.model.UnavailableReason;

import java.time.LocalTime;
import java.util.List;

/**
 * RF-006: la hora pedida no se puede reservar. No se guarda nada y se devuelven hasta N horas
 * libres del mismo dia para que el cliente escoja otra, con el motivo real.
 */
public class SlotUnavailableException extends ConflictException {

    private final List<LocalTime> alternatives;
    private final UnavailableReason reason;

    public SlotUnavailableException(List<LocalTime> alternatives) {
        this(alternatives, UnavailableReason.NO_FREE_BAY);
    }

    public SlotUnavailableException(List<LocalTime> alternatives, UnavailableReason reason) {
        super("SLOT_UNAVAILABLE", messageOf(reason));
        this.alternatives = List.copyOf(alternatives);
        this.reason = reason;
    }

    public List<LocalTime> alternatives() {
        return alternatives;
    }

    public UnavailableReason reason() {
        return reason;
    }

    private static String messageOf(UnavailableReason reason) {
        return switch (reason) {
            case CLOSED_DAY -> "The car wash is closed on the requested date";
            case OUTSIDE_HOURS -> "The service does not fit in the business hours at the requested time";
            case TIME_PASSED -> "The requested time has already passed";
            case NO_FREE_BAY -> "No service bay is free at the requested time";
        };
    }
}
