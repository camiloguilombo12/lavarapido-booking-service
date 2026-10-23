package com.lavarapido.booking.domain.model;

/** Motivo de cancelacion. customerFault queda para reglas futuras (penalizaciones). */
public record CancellationReason(short id, String code, String name, boolean customerFault) {
}
