package com.lavarapido.booking.infrastructure.adapter.in.messaging;

import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.domain.port.in.PaymentReportOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PaymentEventsListener (avisos de pago de payment-service)")
class PaymentEventsListenerTest {

    private final BookingUseCase bookings = mock(BookingUseCase.class);
    private final PaymentEventsListener listener = new PaymentEventsListener(bookings);

    private static Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    /** El sobre que publica payment-service (RabbitMqIntegrationEventPublisher). */
    private static Message event(String eventType, long bookingId) {
        return message("{\"eventId\":\"e-1\",\"eventType\":\"" + eventType + "\",\"aggregateId\":\"31\","
                + "\"occurredAt\":\"2026-10-26T15:10:00.0000000+00:00\",\"version\":1,"
                + "\"payload\":{\"paymentId\":31,\"bookingId\":" + bookingId + ",\"customerUserId\":7,\"amount\":40000.0}}");
    }

    @Test
    @DisplayName("PaymentReported y PaymentConfirmed marcan el pago como reportado")
    void reportedAndConfirmedAreRecorded() {
        given(bookings.recordPaymentReported(50L)).willReturn(PaymentReportOutcome.RECORDED);

        listener.onEvent(event("PaymentReported", 50L));
        verify(bookings).recordPaymentReported(50L);

        given(bookings.recordPaymentReported(51L)).willReturn(PaymentReportOutcome.RECORDED);
        listener.onEvent(event("PaymentConfirmed", 51L));
        verify(bookings).recordPaymentReported(51L);
    }

    @Test
    @DisplayName("idempotente: el mismo aviso repetido no falla (el caso de uso ya no cambia nada)")
    void repeatedMessageIsHarmless() {
        given(bookings.recordPaymentReported(50L))
                .willReturn(PaymentReportOutcome.RECORDED)
                .willReturn(PaymentReportOutcome.ALREADY_REPORTED);

        assertDoesNotThrow(() -> listener.onEvent(event("PaymentReported", 50L)));
        assertDoesNotThrow(() -> listener.onEvent(event("PaymentReported", 50L)));
    }

    @Test
    @DisplayName("una reserva inexistente o cancelada se ignora (solo log), sin reintentos ni DLQ")
    void missingOrCancelledBookingsAreIgnored() {
        given(bookings.recordPaymentReported(404L)).willReturn(PaymentReportOutcome.NOT_FOUND);
        given(bookings.recordPaymentReported(60L)).willReturn(PaymentReportOutcome.CANCELLED);

        assertDoesNotThrow(() -> listener.onEvent(event("PaymentReported", 404L)));
        assertDoesNotThrow(() -> listener.onEvent(event("PaymentConfirmed", 60L)));
    }

    @Test
    @DisplayName("un mensaje que no es JSON va directo a la DLQ; otros eventos o sin bookingId se ignoran")
    void invalidOrForeignMessages() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.onEvent(message("no es json")));

        listener.onEvent(event("PaymentRejected", 50L));
        listener.onEvent(message("{\"eventType\":\"PaymentReported\",\"payload\":{}}"));
        verify(bookings, never()).recordPaymentReported(anyLong());
    }

    @Test
    @DisplayName("si la base no responde, el error sube para que RabbitMQ reintente y luego vaya a la DLQ")
    void databaseDownIsRetried() {
        given(bookings.recordPaymentReported(50L)).willThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(DataAccessResourceFailureException.class, () -> listener.onEvent(event("PaymentReported", 50L)));
    }
}
