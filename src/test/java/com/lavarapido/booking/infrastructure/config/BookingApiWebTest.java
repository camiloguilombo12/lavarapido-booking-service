package com.lavarapido.booking.infrastructure.config;

import com.lavarapido.booking.domain.exception.SlotUnavailableException;
import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.domain.port.in.CatalogUseCase;
import com.lavarapido.booking.domain.port.in.ScheduleUseCase;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP y reglas de seguridad con los casos de uso simulados. */
@WebMvcTest(properties = {
        "security.jwt.secret=" + BookingApiWebTest.SECRET,
        "security.jwt.issuer=" + BookingApiWebTest.ISSUER,
        "security.jwt.audience=" + BookingApiWebTest.AUDIENCE,
        "security.jwt.access-token-ttl=1h",
        "app.internal.api-key=" + BookingApiWebTest.INTERNAL_KEY
})
@Import({SecurityConfig.class, JwtConfig.class, ProblemDetailsSecurityHandler.class, CorrelationIdFilter.class})
class BookingApiWebTest {

    static final String SECRET = "test-secret-with-at-least-thirty-two-bytes!!";
    static final String ISSUER = "lavarapido-security-service";
    static final String AUDIENCE = "lavarapido-api";
    static final String INTERNAL_KEY = "internal-test-key";

    @Autowired
    private MockMvc mvc;

    @MockitoBean private BookingUseCase bookings;
    @MockitoBean private CatalogUseCase catalog;
    @MockitoBean private ScheduleUseCase schedule;

    private static String token(long userId, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of(role))
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void catalogIsPublic() throws Exception {
        given(catalog.services(false, null)).willReturn(List.of());
        mvc.perform(get("/api/v1/catalog/services")).andExpect(status().isOk());
    }

    @Test
    void bookingsNeedAToken() throws Exception {
        mvc.perform(get("/api/v1/bookings/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoutesAreForbiddenForClients() throws Exception {
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/bays").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerRoutesAreOnlyForClients() throws Exception {
        // BIZ-12 / A-10: un admin o un operario no reserva ni ve "mis reservas" como si fuera cliente
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(30L, "OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bookings").header("Authorization", token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":5,\"serviceIds\":[2],\"date\":\"2026-10-01\",\"time\":\"09:00\"}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(bookings);

        given(bookings.mine(any())).willReturn(List.of());
        given(bookings.cancellationReasons()).willReturn(List.of());
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isOk());
        // el formulario de reservas del admin sí lee los motivos (y las horas libres); el operario no
        mvc.perform(get("/api/v1/bookings/cancellation-reasons").header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/bookings/cancellation-reasons").header("Authorization", token(30L, "OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListBookings() throws Exception {
        given(bookings.list(any(), any(), any(), any())).willReturn(List.of());
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void fullSlotAnswersConflictWithAlternatives() throws Exception {
        given(bookings.create(any(), any())).willThrow(
                new SlotUnavailableException(List.of(LocalTime.of(10, 30), LocalTime.of(11, 0))));

        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":5,"serviceIds":[2],"date":"2026-10-01","time":"09:00"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"))
                .andExpect(jsonPath("$.reason").value("NO_FREE_BAY"))
                .andExpect(jsonPath("$.alternatives[0]").value("10:30"));
    }

    @Test
    void databaseErrorsAreUnavailableOnlyWhenTheDatabaseIsDown() throws Exception {
        // BIZ-13 / E-18: base caída es 503; un choque de datos es 409; un error nuestro de SQL es 500
        given(bookings.mine(any()))
                .willThrow(new org.springframework.transaction.CannotCreateTransactionException("no connection"))
                .willThrow(new org.springframework.dao.DataAccessResourceFailureException("connection reset"))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("uq_customer_vehicle"))
                .willThrow(new org.springframework.dao.InvalidDataAccessResourceUsageException("bad sql"));

        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BOOKING_SERVICE_UNAVAILABLE"));
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void aRejectionFromCustomerIsNotUnavailable() throws Exception {
        // BIZ-13: customer respondió 403; booking no lo convierte en 503
        given(bookings.mine(any())).willThrow(
                new com.lavarapido.booking.domain.exception.ForbiddenException("FORBIDDEN", "denied"));

        mvc.perform(get("/api/v1/bookings/me").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void paymentModeTravelsToTheUseCaseAndTheViewShowsThePaymentTerms() throws Exception {
        // contrato de pago: paymentMode llega tal cual; la vista trae paymentMode, paymentDeadline y paymentReported
        java.time.Instant start = java.time.Instant.parse("2026-10-27T14:00:00Z");
        java.time.Instant deadline = java.time.Instant.parse("2026-10-26T15:30:00Z");
        com.lavarapido.booking.domain.model.Booking booking = com.lavarapido.booking.domain.model.Booking.restore(
                99L, 5L, (short) 1, start, start.plusSeconds(3600),
                com.lavarapido.booking.domain.model.BookingStatus.CONFIRMED, 7L, null, 0, java.math.BigDecimal.ZERO,
                null, List.of(), start, com.lavarapido.booking.domain.model.PaymentMode.DIGITAL, deadline, null);
        com.lavarapido.booking.domain.port.in.BookingView view = new com.lavarapido.booking.domain.port.in.BookingView(
                booking, null, null, java.time.LocalDateTime.parse("2026-10-27T09:00"),
                java.time.LocalDateTime.parse("2026-10-27T10:00"), true);
        org.mockito.ArgumentCaptor<com.lavarapido.booking.domain.port.in.BookingRequest> request =
                org.mockito.ArgumentCaptor.forClass(com.lavarapido.booking.domain.port.in.BookingRequest.class);
        given(bookings.create(request.capture(), any())).willReturn(view);

        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":5,"serviceIds":[2],"date":"2026-10-27","time":"09:00","paymentMode":"DIGITAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMode").value("DIGITAL"))
                .andExpect(jsonPath("$.paymentDeadline").value("2026-10-26T15:30:00Z"))
                .andExpect(jsonPath("$.paymentReported").value(false))
                // el cliente no ve el id de quien creo la reserva (un administrador)
                .andExpect(jsonPath("$.bookedBy").doesNotExist());
        org.junit.jupiter.api.Assertions.assertEquals("DIGITAL", request.getValue().paymentMode());

        // una reserva de antes del cambio: sin modo ni plazo
        com.lavarapido.booking.domain.model.Booking legacy = com.lavarapido.booking.domain.model.Booking.restore(
                98L, 5L, (short) 1, start, start.plusSeconds(3600),
                com.lavarapido.booking.domain.model.BookingStatus.CONFIRMED, 7L, null, 0, java.math.BigDecimal.ZERO,
                null, List.of(), start);
        given(bookings.list(any(), any(), any(), any())).willReturn(List.of(new com.lavarapido.booking.domain.port.in.BookingView(
                legacy, null, null, java.time.LocalDateTime.parse("2026-10-27T09:00"),
                java.time.LocalDateTime.parse("2026-10-27T10:00"), true)));
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentMode").doesNotExist())
                .andExpect(jsonPath("$[0].paymentDeadline").doesNotExist())
                .andExpect(jsonPath("$[0].paymentReported").value(false))
                .andExpect(jsonPath("$[0].bookedBy").value(7));
    }

    @Test
    void paymentRuleErrorsKeepTheirCodes() throws Exception {
        given(bookings.create(any(), any()))
                .willThrow(new com.lavarapido.booking.domain.exception.InvalidValueException(
                        "INVALID_PAYMENT_MODE", "Choose how the booking will be paid"))
                .willThrow(new com.lavarapido.booking.domain.exception.ConflictException(
                        "CASH_BOOKING_LIMIT", "limit"));
        String body = """
                {"vehicleId":5,"serviceIds":[2],"date":"2026-10-27","time":"09:00"}
                """;

        mvc.perform(post("/api/v1/bookings").header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_MODE"));
        mvc.perform(post("/api/v1/bookings").header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_BOOKING_LIMIT"));
    }

    @Test
    void invalidBodyIsAValidationError() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nullOrTooManyServicesAreValidationErrors() throws Exception {
        // [null] llegaba al caso de uso y respondia 500; una lista sin tope rompia el IN de SQL Server
        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":5,\"serviceIds\":[null],\"date\":\"2026-11-02\",\"time\":\"09:00\",\"paymentMode\":\"CASH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":5,\"serviceIds\":[1,2,3,4,5,6,7,8,9,10,11],\"date\":\"2026-11-02\",\"time\":\"09:00\",\"paymentMode\":\"CASH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/bookings/9")
                        .header("Authorization", token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[null],\"date\":\"2026-11-02\",\"time\":\"09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        org.mockito.Mockito.verifyNoInteractions(bookings);
    }

    @Test
    void anInvalidDayInTheWeekIsAValidationError() throws Exception {
        // la lista se valida elemento por elemento: un dia 9 o sin "working" respondia 500
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/schedule/business-hours")
                        .header("Authorization", token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"dayOfWeek\":9,\"working\":null,\"opensAt\":\"08:00\",\"closesAt\":\"18:00\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/schedule/business-hours")
                        .header("Authorization", token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        org.mockito.Mockito.verifyNoInteractions(schedule);
    }

    @Test
    void theCustomerCancelSendsTheChosenReason() throws Exception {
        // A-20: antes el controlador descartaba reasonCode y siempre mandaba null
        given(bookings.cancel(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("SCHEDULE_CONFLICT"), any()))
                .willThrow(new com.lavarapido.booking.domain.exception.InvalidValueException("CHOSEN", "llego el motivo"));

        mvc.perform(post("/api/v1/bookings/7/cancel").header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SCHEDULE_CONFLICT\"}"))
                .andExpect(jsonPath("$.code").value("CHOSEN"));
    }

    @Test
    void scheduleConflictsSayHowManyBookingsFallOutside() throws Exception {
        // BIZ-10: quitar la excepción deja reservas por fuera; la respuesta dice cuántas y cuáles
        org.mockito.BDDMockito.willThrow(new com.lavarapido.booking.domain.exception.ScheduleConflictException(
                        List.of("RES-000009", "RES-000010")))
                .given(schedule).deleteException(3, 1L);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/schedule/exceptions/3").header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICTS_BOOKINGS"))
                .andExpect(jsonPath("$.conflictingBookings").value(2))
                .andExpect(jsonPath("$.bookingCodes[1]").value("RES-000010"));
    }

    @Test
    void aStaleBookingAnswersConflict() throws Exception {
        // BIZ-09: otra petición cambió la reserva entre la lectura y el guardado (row_version)
        given(bookings.cancel(org.mockito.ArgumentMatchers.anyLong(), any(), any())).willThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Object.class, 7L));

        mvc.perform(post("/api/v1/bookings/7/cancel").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void operatorRoutesAreForOperatorsAndAdmins() throws Exception {
        given(bookings.operatorDay(any(), any(), any())).willReturn(List.of());
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization", token(30L, "OPERATOR"))
                        .header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization", token(1L, "ADMIN"))
                        .header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization", token(7L, "CLIENT"))
                        .header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", token(30L, "OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorRoutesOnlyComeThroughOperationsService() throws Exception {
        // E-01: un operario con su token, pero sin la llave interna (no viene de operations), no lee
        // ni mueve reservas de booking directamente: la asignacion la valida operations-service
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization", token(30L, "OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/bookings/5").header("Authorization", token(30L, "OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/operator/bookings/5/status").header("Authorization", token(30L, "OPERATOR"))
                        .header("X-Internal-Key", "otra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(bookings);
    }

    @Test
    void internalRoutesOnlyAcceptTheInternalKey() throws Exception {
        given(bookings.listForService(any(), any())).willReturn(java.util.List.of());

        mvc.perform(get("/internal/v1/bookings").param("from", "2026-10-14").param("to", "2026-10-14"))
                .andExpect(status().isUnauthorized());
        // ni siquiera el token de un admin abre las rutas internas
        mvc.perform(get("/internal/v1/bookings").param("from", "2026-10-14").param("to", "2026-10-14")
                        .header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/bookings").param("from", "2026-10-14").param("to", "2026-10-14")
                        .header("X-Internal-Key", "otra"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/bookings").param("from", "2026-10-14").param("to", "2026-10-14")
                        .header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void activeCountIsInternalAndAnswersTheSharedContract() throws Exception {
        // BIZ-08: customer-service lo pide con la llave interna antes de retirar un vehiculo
        given(bookings.activeBookingsOf(15L)).willReturn(2L);

        mvc.perform(get("/internal/v1/bookings/active-count").param("customerVehicleId", "15")
                        .header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/bookings/active-count").param("customerVehicleId", "15")
                        .header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerVehicleId").value(15))
                .andExpect(jsonPath("$.activeBookings").value(2));
    }

    @Test
    void onlyOperationsCanCloseABookingAsNotAttended() throws Exception {
        given(bookings.markUnattended(7L)).willThrow(new com.lavarapido.booking.domain.exception.ConflictException(
                "BOOKING_NOT_EXPIRED", "not yet"));

        // ni el admin con su token: esta decision es del sistema (sin operario asignado)
        mvc.perform(post("/internal/v1/bookings/7/no-show").header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/bookings/7/no-show").header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_EXPIRED"));
    }
}
