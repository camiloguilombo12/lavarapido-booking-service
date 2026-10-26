package com.lavarapido.booking.domain.port.out;

/**
 * Consulta a payment-service si la reserva ya tiene un pago reportado (adenda del contrato de pago,
 * 26-10-2026). El aviso payment.reported puede perderse (no hay Outbox), asi que antes de cancelar
 * por falta de pago se le pregunta directo a payment.
 */
public interface PaymentStatusDirectory {

    /**
     * true si la reserva tiene algun pago en revision o aprobado; false solo si payment lo dice
     * explicitamente. Si payment no responde, responde con error o con algo que no se entiende, lanza
     * DependencyUnavailableException: nunca se asume que no hay pago.
     */
    boolean paymentReported(long bookingId);
}
