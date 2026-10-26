package com.lavarapido.booking.domain.port.in;

/** Que paso al revisar una reserva DIGITAL vencida (PaymentDeadlineJob lo registra en el log). */
public enum PaymentTimeoutOutcome {
    /** payment confirmo que no hay pago: se cancelo con PAYMENT_TIMEOUT. */
    CANCELLED,
    /** payment tenia el pago (el aviso se habia perdido): se marco como reportado y no se cancelo. */
    PAYMENT_REPORTED,
    /** Ya no vence: llego el comprobante, empezo, se cancelo o no existe. */
    NOT_OVERDUE
}
