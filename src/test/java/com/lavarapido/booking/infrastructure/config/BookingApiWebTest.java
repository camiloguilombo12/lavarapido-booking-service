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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP y reglas de seguridad con los casos de uso simulados. */
@WebMvcTest(properties = {
        "security.jwt.secret=" + BookingApiWebTest.SECRET,
        "security.jwt.issuer=" + BookingApiWebTest.ISSUER,
        "security.jwt.audience=" + BookingApiWebTest.AUDIENCE,
        "security.jwt.access-token-ttl=1h"
})
@Import({SecurityConfig.class, JwtConfig.class, ProblemDetailsSecurityHandler.class, CorrelationIdFilter.class})
class BookingApiWebTest {

    static final String SECRET = "test-secret-with-at-least-thirty-two-bytes!!";
    static final String ISSUER = "lavarapido-security-service";
    static final String AUDIENCE = "lavarapido-api";

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
    void operatorRoutesAreForOperatorsAndAdmins() throws Exception {
        given(bookings.operatorDay(any(), any(), any())).willReturn(List.of());
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization", token(30L, "OPERATOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization", token(1L, "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization", token(7L, "CLIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", token(30L, "OPERATOR")))
                .andExpect(status().isForbidden());
    }
}
