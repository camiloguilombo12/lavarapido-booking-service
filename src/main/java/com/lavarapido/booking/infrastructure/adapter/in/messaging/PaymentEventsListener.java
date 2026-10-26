package com.lavarapido.booking.infrastructure.adapter.in.messaging;

import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.domain.port.in.PaymentReportOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/**
 * Adaptador de entrada (D): payment-service avisa que el cliente reporto el pago (PaymentReported) o
 * que se aprobo (PaymentConfirmed, por ejemplo el efectivo que registra el admin). La reserva guarda
 * payment_reported_at una sola vez y deja de vencer.
 *
 * Sobre de carwash.events: {eventId, eventType, aggregateId, occurredAt, version, payload}; el id de
 * la reserva va en payload.bookingId (PaymentIntegrationEvents de payment-service).
 *
 * El mismo mensaje puede llegar dos veces: la segunda no cambia nada. Una reserva que no existe o
 * ya esta cancelada se ignora y queda en el log. Un mensaje que no es JSON va directo a la DLQ; un
 * error de la base se relanza para que RabbitMQ lo reintente y, si sigue, lo deje en la DLQ.
 */
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);
    private static final Set<String> HANDLED = Set.of("PaymentReported", "PaymentConfirmed");

    private final BookingUseCase bookings;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    PaymentEventsListener(BookingUseCase bookings) {
        this.bookings = bookings;
    }

    @RabbitListener(queues = RabbitConsumerConfig.QUEUE)
    void onEvent(Message message) {
        JsonNode event;
        try {
            event = json.readTree(message.getBody());
        } catch (JacksonException e) {
            log.warn("Message on {} is not a valid event envelope, sent to the DLQ", RabbitConsumerConfig.QUEUE);
            throw new AmqpRejectAndDontRequeueException("Invalid event envelope", e);
        }
        String eventType = event.path("eventType").asString("");
        if (!HANDLED.contains(eventType)) {
            return;
        }
        long bookingId = event.path("payload").path("bookingId").asLong(0);
        if (bookingId <= 0) {
            log.warn("{} ({}) without a bookingId, ignored", eventType, event.path("eventId").asString(""));
            return;
        }
        PaymentReportOutcome outcome = bookings.recordPaymentReported(bookingId);
        switch (outcome) {
            case RECORDED -> log.info("Booking {}: payment reported ({})", bookingId, eventType);
            case ALREADY_REPORTED -> log.debug("Booking {}: payment was already reported ({})", bookingId, eventType);
            case NOT_FOUND -> log.warn("{} for booking {} ignored: the booking does not exist", eventType, bookingId);
            case CANCELLED -> log.warn("{} for booking {} ignored: the booking is already cancelled", eventType,
                    bookingId);
        }
    }
}
