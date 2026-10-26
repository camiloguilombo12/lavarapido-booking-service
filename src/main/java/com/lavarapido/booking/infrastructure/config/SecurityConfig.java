package com.lavarapido.booking.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API sin estado protegida con JWT Bearer (ADR-006). CSRF apagada porque ninguna cookie lleva
 * credenciales.
 *
 * Publico (solo lectura): el catalogo, el horario y los datos de la sede, que tambien ve la
 * landing sin sesion. Reservar exige sesion. /api/v1/admin/** exige el rol ADMIN y
 * /api/v1/bookings/** el rol CLIENT (BIZ-12).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
class SecurityConfig {

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/catalog/**",
            "/api/v1/schedule/**",
            "/api/v1/establishment",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    /** Rutas del cliente (BIZ-12): un admin o un operario no reserva ni ve "mis reservas". */
    private static final String[] CLIENT_ENDPOINTS = {
            "/api/v1/bookings",
            "/api/v1/bookings/**"
    };

    /** Lecturas del cliente que tambien usa el formulario de reservas del admin. */
    private static final String[] CLIENT_AND_ADMIN_GET_ENDPOINTS = {
            "/api/v1/bookings/availability",
            "/api/v1/bookings/cancellation-reasons"
    };

    /** Swagger UI y el JSON de OpenAPI. Solo existen con el perfil dev (application-dev.yml). */
    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    /**
     * Rutas entre servicios (/internal/**): solo la llave interna (InternalApiKeyFilter), nunca el JWT
     * de un usuario. Fuera de /api/v1 para que el gateway no las exponga. Se revisa antes que la API.
     */
    @Bean
    @Order(1)
    SecurityFilterChain internalFilterChain(HttpSecurity http, ProblemDetailsSecurityHandler entryPoint,
                                            @Value("${app.internal.api-key:}") String internalApiKey) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalApiKeyFilter(internalApiKey), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("INTERNAL"))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, ProblemDetailsSecurityHandler entryPoint,
                                    @Value("${app.internal.api-key:}") String internalApiKey) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // E-01: solo operations-service, con el token del operario y la llave interna
                        .requestMatchers("/api/v1/operator/**").access(throughOperations(internalApiKey))
                        // BIZ-12 / A-10: las reservas "mias" son solo del rol CLIENT. Las horas libres y los
                        // motivos de cancelacion tambien los lee el admin (formulario de reservas de la web)
                        .requestMatchers(HttpMethod.GET, CLIENT_AND_ADMIN_GET_ENDPOINTS).hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(CLIENT_ENDPOINTS).hasRole("CLIENT")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter()))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint));
        return http.build();
    }

    /**
     * E-01: /api/v1/operator/** exige el rol OPERATOR o ADMIN y, además, la llave interna. Así solo
     * llega operations-service, que valida que la reserva esté asignada a ese operario; un operario
     * que llame a booking por su cuenta recibe 403. El gateway tampoco publica estas rutas.
     */
    private static AuthorizationManager<RequestAuthorizationContext> throughOperations(String internalApiKey) {
        AuthorizationManager<RequestAuthorizationContext> staff = AuthorityAuthorizationManager.hasAnyRole("OPERATOR", "ADMIN");
        InternalApiKeyFilter key = new InternalApiKeyFilter(internalApiKey);
        return (authentication, context) -> {
            AuthorizationResult byRole = staff.authorize(authentication, context);
            return new AuthorizationDecision(byRole != null && byRole.isGranted() && key.matches(context.getRequest()));
        };
    }

    /** El claim roles del token (["CLIENT"], ["ADMIN"]...) se vuelve ROLE_CLIENT, ROLE_ADMIN. */
    private static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
