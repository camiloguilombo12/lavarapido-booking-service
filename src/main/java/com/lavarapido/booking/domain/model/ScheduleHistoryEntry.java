package com.lavarapido.booking.domain.model;

import java.time.Instant;

/** Un movimiento en el historial de "Horarios y bahias" (solo lectura: se crea, nunca se edita). */
public record ScheduleHistoryEntry(long id, ScheduleEntityType entityType, String title, String detail,
                                   Instant changedAt, Long changedBy) {
}
