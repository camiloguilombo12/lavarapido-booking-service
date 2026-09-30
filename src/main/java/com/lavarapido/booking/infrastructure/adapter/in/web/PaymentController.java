package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.model.Payment;
import com.lavarapido.booking.domain.port.out.PaymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controlador REST para los pagos
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // listar pagos de una reserva
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<Payment>> listByBooking(@PathVariable Long bookingId) {
        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        return ResponseEntity.ok(payments);
    }
}
