package com.lavarapido.booking.infrastructure.adapter.out.payment;

import com.lavarapido.booking.domain.exception.DependencyUnavailableException;
import com.lavarapido.booking.domain.port.out.PaymentStatusDirectory;
import com.lavarapido.booking.infrastructure.config.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Pregunta a payment-service por REST (ADR-004) si una reserva ya tiene el pago reportado
 * (GET /internal/v1/payments/bookings/{id}/reported). Lo usa PaymentDeadlineJob, que no tiene usuario
 * detras: va con la llave entre servicios (X-Internal-Key = INTERNAL_API_KEY), la misma que booking
 * exige en sus rutas /internal/**.
 *
 * Mismo cliente y mismo estilo de timeout que CustomerServiceDirectory, con un timeout corto (3 s).
 * Cualquier fallo (caido, timeout, 4xx/5xx, respuesta sin "reported") es
 * PAYMENT_SERVICE_UNAVAILABLE: el job no cancela esa reserva y la vuelve a mirar en la siguiente vuelta.
 */
@Component
class PaymentServiceDirectory implements PaymentStatusDirectory {

    static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceDirectory.class);

    private final RestClient client;
    private final String internalKey;

    PaymentServiceDirectory(@Value("${app.payment-service.base-url}") String baseUrl,
                            @Value("${app.payment-service.timeout:3s}") Duration timeout,
                            @Value("${app.internal.api-key:}") String internalKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalKey = internalKey == null ? "" : internalKey;
    }

    @Override
    public boolean paymentReported(long bookingId) {
        ReportedDto answer;
        try {
            answer = client.get()
                    .uri("/internal/v1/payments/bookings/{id}/reported", bookingId)
                    .header(INTERNAL_KEY_HEADER, internalKey)
                    .headers(PaymentServiceDirectory::trace)
                    .retrieve()
                    .body(ReportedDto.class);
        } catch (RestClientException e) {
            log.warn("payment-service did not tell whether booking {} has a reported payment: {}", bookingId,
                    e.getMessage());
            throw unavailable();
        }
        if (answer == null || answer.reported() == null) {
            log.warn("payment-service answered without 'reported' for booking {}", bookingId);
            throw unavailable();
        }
        return answer.reported();
    }

    /** cross-cutting.md §4: si hay X-Correlation-Id en el contexto, viaja con la llamada. */
    private static void trace(HttpHeaders headers) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.add(CorrelationIdFilter.HEADER, correlationId);
        }
    }

    private static DependencyUnavailableException unavailable() {
        return new DependencyUnavailableException("PAYMENT_SERVICE_UNAVAILABLE",
                "Could not check the payment of the booking with payment-service");
    }

    /** La respuesta de payment-service: {"bookingId": 123, "reported": true}. */
    record ReportedDto(Long bookingId, Boolean reported) {
    }
}
