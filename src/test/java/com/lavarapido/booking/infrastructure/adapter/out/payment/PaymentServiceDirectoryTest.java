package com.lavarapido.booking.infrastructure.adapter.out.payment;

import com.lavarapido.booking.domain.exception.DependencyUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adenda: booking le pregunta a payment si hay pago antes de cancelar; ante la duda, no cancela. */
@DisplayName("Cliente de payment-service (¿la reserva tiene pago?)")
class PaymentServiceDirectoryTest {

    private static final String KEY = "internal-test-key";

    private HttpServer server;
    private PaymentServiceDirectory directory;
    private volatile int status;
    private volatile String body;
    private volatile long delayMillis;
    private volatile String receivedKey;
    private volatile String receivedPath;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal", exchange -> {
            receivedKey = exchange.getRequestHeaders().getFirst("X-Internal-Key");
            receivedPath = exchange.getRequestURI().getPath();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        directory = new PaymentServiceDirectory("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofMillis(500), KEY);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(int httpStatus, String json) {
        status = httpStatus;
        body = json;
    }

    @Test
    @DisplayName("lee reported=true / false y va a la ruta interna con la llave entre servicios")
    void readsTheAnswerWithTheInternalKey() {
        answer(200, "{\"bookingId\":123,\"reported\":true}");
        assertTrue(directory.paymentReported(123L));
        assertEquals(KEY, receivedKey);
        assertEquals("/internal/v1/payments/bookings/123/reported", receivedPath);

        answer(200, "{\"bookingId\":123,\"reported\":false}");
        assertFalse(directory.paymentReported(123L));
    }

    @Test
    @DisplayName("un error de payment (401, 404, 500) no se toma como 'sin pago': es PAYMENT_SERVICE_UNAVAILABLE")
    void errorsAreUnavailableNotFalse() {
        for (int code : new int[] {401, 403, 404, 500, 503}) {
            answer(code, "{\"code\":\"X\"}");
            DependencyUnavailableException error = assertThrows(DependencyUnavailableException.class,
                    () -> directory.paymentReported(123L));
            assertEquals("PAYMENT_SERVICE_UNAVAILABLE", error.code());
        }
    }

    @Test
    @DisplayName("una respuesta sin 'reported' tampoco cuenta como 'sin pago'")
    void missingFieldIsUnavailable() {
        answer(200, "{\"bookingId\":123}");
        assertThrows(DependencyUnavailableException.class, () -> directory.paymentReported(123L));
    }

    @Test
    @DisplayName("si payment tarda más que el timeout, es PAYMENT_SERVICE_UNAVAILABLE")
    void timeoutIsUnavailable() {
        answer(200, "{\"bookingId\":123,\"reported\":false}");
        delayMillis = 1500;

        assertThrows(DependencyUnavailableException.class, () -> directory.paymentReported(123L));
    }

    @Test
    @DisplayName("si payment está caído (nadie escucha en el puerto), es PAYMENT_SERVICE_UNAVAILABLE")
    void downIsUnavailable() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        PaymentServiceDirectory down = new PaymentServiceDirectory("http://127.0.0.1:" + freePort,
                Duration.ofMillis(500), KEY);

        assertThrows(DependencyUnavailableException.class, () -> down.paymentReported(123L));
    }
}
