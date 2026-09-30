package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.Payment;

import java.util.List;

// puerto para consultar los pagos
public interface PaymentRepository {

    List<Payment> findByBookingId(Long bookingId);
}
