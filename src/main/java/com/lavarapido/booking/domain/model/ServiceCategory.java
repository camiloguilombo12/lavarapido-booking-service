package com.lavarapido.booking.domain.model;

/** Categoria del catalogo (LAVADO, POLICHADO, DETALLADO, INTERIOR, PAQUETES). */
public record ServiceCategory(short id, String code, String name, short displayOrder) {
}
