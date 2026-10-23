package com.lavarapido.booking.domain.exception;

/** Dato que no cumple una regla (400). */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String code, String message) {
        super(code, message);
    }
}
