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
    void invalidBodyIsAValidationError() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", token(7L, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
