package com.lavarapido.booking.infrastructure.adapter.out.customer;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.DependencyUnavailableException;
import com.lavarapido.booking.domain.exception.ForbiddenException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BIZ-13 / E-18: que customer rechace una consulta (4xx) no es lo mismo que customer caido (503). */
@DisplayName("Cliente de customer-service")
class CustomerServiceDirectoryTest {

    private HttpServer server;
    private CustomerServiceDirectory directory;
    private volatile int status;
    private volatile String body;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        directory = new CustomerServiceDirectory("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                Duration.ofSeconds(2));
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("7").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void stop() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    private void answer(int httpStatus, String json) {
        status = httpStatus;
        body = json;
    }

    @Test
    @DisplayName("un 409 de customer sale como 409 con su mismo code, no como 503")
    void aConflictKeepsTheCodeOfCustomer() {
        answer(409, "{\"status\":409,\"code\":\"CUSTOMER_NOT_PROVISIONED\"}");

        ConflictException error = assertThrows(ConflictException.class, () -> directory.ownVehicles());
        assertEquals("CUSTOMER_NOT_PROVISIONED", error.code());
    }

    @Test
    @DisplayName("un 403 de customer es 403 FORBIDDEN; un 400 es 400 con el code de customer")
    void forbiddenAndBadRequestAreNotUnavailable() {
        answer(403, "{\"code\":\"FORBIDDEN\"}");
        assertEquals("FORBIDDEN", assertThrows(ForbiddenException.class,
                () -> directory.vehiclesByIds(List.of(5L))).code());

        answer(400, "{\"code\":\"VALIDATION_ERROR\"}");
        assertEquals("VALIDATION_ERROR", assertThrows(InvalidValueException.class,
                () -> directory.vehiclesByIds(List.of(5L))).code());
    }

    @Test
    @DisplayName("un 404 sigue siendo 'no es tuyo' y un 5xx sigue siendo 503")
    void notFoundIsEmptyAndServerErrorsAreUnavailable() {
        answer(404, "{\"code\":\"VEHICLE_NOT_FOUND\"}");
        assertTrue(directory.ownVehicle(5L).isEmpty());

        answer(503, "");
        assertEquals("CUSTOMER_SERVICE_UNAVAILABLE", assertThrows(DependencyUnavailableException.class,
                () -> directory.ownVehicle(5L)).code());
    }

    @Test
    @DisplayName("customer apagado es 503 CUSTOMER_SERVICE_UNAVAILABLE")
    void customerDownIsUnavailable() throws IOException {
        int closedPort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        CustomerServiceDirectory down = new CustomerServiceDirectory("http://127.0.0.1:" + closedPort + "/api/v1",
                Duration.ofSeconds(2));

        assertThrows(DependencyUnavailableException.class, down::ownVehicles);
    }
}
