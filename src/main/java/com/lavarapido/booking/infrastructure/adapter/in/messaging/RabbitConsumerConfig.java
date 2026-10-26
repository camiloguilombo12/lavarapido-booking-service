package com.lavarapido.booking.infrastructure.adapter.in.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cola de booking-service en carwash.events (ADR-004): los avisos de pago de payment-service, para
 * saber que el cliente ya subio el comprobante (payment.reported) o que el pago se aprobo
 * (payment.confirmed). Con eso la reserva DIGITAL ya no vence (PaymentDeadlineJob).
 *
 * Como en operations y notification: un mensaje que sigue fallando despues de los reintentos
 * (spring.rabbitmq.listener.simple.retry) va a la DLQ booking.payment-events.dlq por el exchange
 * compartido carwash.events.dlx, en vez de perderse. El exchange carwash.events lo declara
 * RabbitMessagingConfig (el publicador de este mismo servicio).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
class RabbitConsumerConfig {

    static final String QUEUE = "booking.payment-events";
    static final String DEAD_LETTER_EXCHANGE = "carwash.events.dlx";
    static final String DEAD_LETTER_QUEUE = "booking.payment-events.dlq";
    static final String REPORTED_KEY = "payment.reported";
    static final String CONFIRMED_KEY = "payment.confirmed";

    @Bean
    Declarables bookingPaymentTopology(TopicExchange carwashEvents) {
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        Queue deadLetterQueue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
        Queue queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
        Binding deadLetters = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_QUEUE);
        Binding reported = BindingBuilder.bind(queue).to(carwashEvents).with(REPORTED_KEY);
        Binding confirmed = BindingBuilder.bind(queue).to(carwashEvents).with(CONFIRMED_KEY);
        return new Declarables(deadLetterExchange, deadLetterQueue, queue, deadLetters, reported, confirmed);
    }
}
