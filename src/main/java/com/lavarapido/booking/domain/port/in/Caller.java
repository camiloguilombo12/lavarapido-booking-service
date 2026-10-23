package com.lavarapido.booking.domain.port.in;

/** Quien hace la peticion, leido del token: su user_id y si es administrador. */
public record Caller(long userId, boolean admin) {
}
