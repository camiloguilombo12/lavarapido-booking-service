package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

// reserva de un lavado hecha por un cliente
public record Booking(
    Long bookingId,
    String bookingCode,
    Long customerId,
    Long vehicleId,
    Long serviceOfferingId,
    Long locationId,
    LocalDate bookingDate,
    LocalTime bookingTime,
    BookingStatus status,
    BigDecimal totalAmount,
    String notes
) {}
