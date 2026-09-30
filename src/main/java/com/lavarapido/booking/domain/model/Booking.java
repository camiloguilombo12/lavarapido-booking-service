package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * La reserva: el vehiculo, la franja, la bahia, el estado y las lineas de servicio.
 *
 * Protege las reglas que tienen que cumplirse juntas (entities-and-rules.md):
 * INV-BOOK-003 (al menos una linea), INV-BOOK-004 (rango valido), INV-BOOK-007 (solo se cambia
 * o cancela antes de empezar) y las transiciones de estado. Las reglas que necesitan mirar otras
 * reservas (bahia libre, INV-BOOK-006) las resuelve AvailabilityPolicy antes de llamar aqui.
 */
public class Booking {

    private static final int MAX_NOTES = 300;

    private long id;
    private final long customerVehicleId;
    private Short serviceBayId;
    private Instant scheduledStart;
    private Instant scheduledEnd;
    private BookingStatus status;
    private final long bookedBy;
    private CancellationReason cancellationReason;
    private final int pointsRedeemed;
    private final BigDecimal pointsDiscountAmount;
    private String notes;
    private List<BookingLine> lines;
    private final Instant createdAt;

    private Booking(long id, long customerVehicleId, Short serviceBayId, Instant scheduledStart, Instant scheduledEnd,
                    BookingStatus status, long bookedBy, CancellationReason cancellationReason, int pointsRedeemed,
                    BigDecimal pointsDiscountAmount, String notes, List<BookingLine> lines, Instant createdAt) {
        this.id = id;
        this.customerVehicleId = customerVehicleId;
        this.serviceBayId = serviceBayId;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.status = status;
        this.bookedBy = bookedBy;
        this.cancellationReason = cancellationReason;
        this.pointsRedeemed = pointsRedeemed;
        this.pointsDiscountAmount = pointsDiscountAmount;
        this.notes = notes;
        this.lines = List.copyOf(lines);
        this.createdAt = createdAt;
    }

    /**
     * Reserva nueva, ya con bahia: RF-006 dice que si hay bahia libre se confirma de una vez.
     * Si no hay, quien llama no llega aqui (SlotUnavailableException).
     */
    public static Booking confirmNew(long customerVehicleId, List<BookingLine> lines, Instant start, Instant end,
                                     short bayId, long bookedBy, String notes, Instant now) {
        if (customerVehicleId <= 0) {
            throw new InvalidValueException("INVALID_VEHICLE", "A booking requires a customer vehicle");
        }
        requireLines(lines);
        requireRange(start, end);
        return new Booking(0L, customerVehicleId, bayId, start, end, BookingStatus.CONFIRMED, bookedBy, null,
                0, BigDecimal.ZERO, cleanNotes(notes), lines, now);
    }

    public static Booking restore(long id, long customerVehicleId, Short serviceBayId, Instant start, Instant end,
                                  BookingStatus status, long bookedBy, CancellationReason reason, int pointsRedeemed,
                                  BigDecimal pointsDiscountAmount, String notes, List<BookingLine> lines,
                                  Instant createdAt) {
        return new Booking(id, customerVehicleId, serviceBayId, start, end, status, bookedBy, reason, pointsRedeemed,
                pointsDiscountAmount, notes, lines, createdAt);
    }

    /** Cambia franja, bahia y (si llegan) servicios. Solo antes de empezar (RF-007). */
    public void reschedule(List<BookingLine> newLines, Instant start, Instant end, short bayId, String newNotes) {
        requireChangeable();
        requireLines(newLines);
        requireRange(start, end);
        this.lines = List.copyOf(newLines);
        this.scheduledStart = start;
        this.scheduledEnd = end;
        this.serviceBayId = bayId;
        this.notes = cleanNotes(newNotes);
        if (status == BookingStatus.SCHEDULED) {
            status = BookingStatus.CONFIRMED;
        }
    }

    /** Cancelar libera la bahia para otras reservas (la fila se queda con su bahia para el historial). */
    public void cancel(CancellationReason reason) {
        requireChangeable();
        if (reason == null) {
            throw new InvalidValueException("INVALID_REASON", "A cancellation reason is required");
        }
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    /**
     * Cambios de estado del admin. CANCELLED va por cancel() porque necesita el motivo.
     * CONFIRMED -> IN_PROGRESS -> COMPLETED; CONFIRMED -> NO_SHOW; SCHEDULED -> CONFIRMED.
     */
    public void moveTo(BookingStatus target) {
        boolean allowed = switch (target) {
            case CONFIRMED -> status == BookingStatus.SCHEDULED && serviceBayId != null;
            case IN_PROGRESS, NO_SHOW -> status == BookingStatus.CONFIRMED;
            case COMPLETED -> status == BookingStatus.IN_PROGRESS;
            case SCHEDULED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new ConflictException("INVALID_STATUS_TRANSITION",
                    "A booking cannot go from " + status + " to " + target);
        }
        this.status = target;
    }

    /** El cliente solo puede cambiarla antes de la hora de inicio y si no ha empezado. */
    public boolean changeableByCustomer(Instant now) {
        return status.isChangeable() && now.isBefore(scheduledStart);
    }

    public BigDecimal subtotal() {
        return lines.stream().map(BookingLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** No se guarda (el SQL no tiene columna total): se calcula para que nunca quede viejo. */
    public BigDecimal total() {
        return subtotal().subtract(pointsDiscountAmount).max(BigDecimal.ZERO);
    }

    public int durationMinutes() {
        return lines.stream().mapToInt(BookingLine::minutes).sum();
    }

    /** Codigo que ve la gente: sale del id, asi no hace falta otra columna. */
    public String code() {
        return codeOf(id);
    }

    public static String codeOf(long bookingId) {
        return String.format("RES-%06d", bookingId);
    }

    public void assignId(long newId) {
        if (id != 0L) {
            throw new IllegalStateException("The booking already has an id");
        }
        this.id = newId;
    }

    private void requireChangeable() {
        if (!status.isChangeable()) {
            throw new ConflictException("BOOKING_NOT_CHANGEABLE",
                    "A booking in status " + status + " can no longer be changed");
        }
    }

    private static void requireLines(List<BookingLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidValueException("SERVICES_REQUIRED", "A booking needs at least one service");
        }
        Set<Long> seen = new HashSet<>();
        for (BookingLine line : lines) {
            if (line.quantity() <= 0) {
                throw new InvalidValueException("INVALID_QUANTITY", "Quantity must be greater than zero");
            }
            if (!seen.add(line.servicePriceId())) {
                throw new InvalidValueException("DUPLICATED_SERVICE", "The same service cannot be added twice");
            }
        }
    }

    private static void requireRange(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidValueException("INVALID_RANGE", "The booking must end after it starts");
        }
    }

    private static String cleanNotes(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String notes = raw.trim();
        if (notes.length() > MAX_NOTES) {
            throw new InvalidValueException("INVALID_NOTES", "Notes can have at most " + MAX_NOTES + " characters");
        }
        return notes;
    }

    public long id() {
        return id;
    }

    public long customerVehicleId() {
        return customerVehicleId;
    }

    public Short serviceBayId() {
        return serviceBayId;
    }

    public Instant scheduledStart() {
        return scheduledStart;
    }

    public Instant scheduledEnd() {
        return scheduledEnd;
    }

    public BookingStatus status() {
        return status;
    }

    public long bookedBy() {
        return bookedBy;
    }

    public CancellationReason cancellationReason() {
        return cancellationReason;
    }

    public int pointsRedeemed() {
        return pointsRedeemed;
    }

    public BigDecimal pointsDiscountAmount() {
        return pointsDiscountAmount;
    }

    public String notes() {
        return notes;
    }

    public List<BookingLine> lines() {
        return lines;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
