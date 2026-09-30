package com.lavarapido.booking.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// pago asociado a una reserva
public record Payment(
    Long paymentId,
    Long bookingId,
    BigDecimal amount,
    String paymentMethod,      // NEQUI, DAVIPLATA, TRANSFER, CASH
    String status,            // PENDING, PAID, FAILED
    String transactionRef,
    LocalDateTime paidAt
) {}
