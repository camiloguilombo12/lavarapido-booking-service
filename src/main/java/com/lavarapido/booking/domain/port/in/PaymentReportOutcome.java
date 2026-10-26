package com.lavarapido.booking.domain.port.in;

/** Qué paso con el aviso de pago reportado que mando payment-service (para el log del consumidor). */
public enum PaymentReportOutcome {
    /** Se guardo payment_reported_at: la reserva ya no vence por falta de pago. */
    RECORDED,
    /** Ya estaba reportado (el mensaje llego repetido, o el pago se aprobo despues del comprobante). */
    ALREADY_REPORTED,
    /** La reserva no existe (o se borro). */
    NOT_FOUND,
    /** La reserva ya estaba cancelada: no se toca. */
    CANCELLED
}
