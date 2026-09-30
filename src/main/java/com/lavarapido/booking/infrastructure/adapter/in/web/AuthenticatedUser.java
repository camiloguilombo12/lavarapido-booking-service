package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.port.in.Caller;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quien llama, leido de un token ya verificado. El claim sub es el user_id de app_user.
 * Los endpoints del cliente siempre lo tratan como cliente; los de /admin, como administrador
 * (SecurityConfig ya exigio el rol ADMIN para entrar ahi).
 */
final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    static long userId(Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidValueException("INVALID_TOKEN", "The token has no subject claim");
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new InvalidValueException("INVALID_TOKEN", "The token subject is not a valid user id");
        }
    }

    static Caller customer(Jwt jwt) {
        return new Caller(userId(jwt), false);
    }

    static Caller admin(Jwt jwt) {
        return new Caller(userId(jwt), true);
    }
}
