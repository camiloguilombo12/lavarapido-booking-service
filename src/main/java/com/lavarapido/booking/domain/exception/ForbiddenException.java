package com.lavarapido.booking.domain.exception;

/**
 * El servicio vecino (customer-service) contesto que quien llama no tiene permiso para esa consulta
 * (401 o 403). Sale 403, no 503: el vecino si respondio (BIZ-13).
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
