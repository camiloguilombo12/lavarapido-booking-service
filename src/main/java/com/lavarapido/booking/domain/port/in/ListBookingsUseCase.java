package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.Booking;

import java.util.List;

// caso de uso para listar reservas de un cliente
public interface ListBookingsUseCase {

    List<Booking> listByCustomer(Long customerId);
}
