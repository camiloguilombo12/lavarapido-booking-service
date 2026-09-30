package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.port.in.CreateBookingUseCase;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.port.out.ServiceTypeRepository;
import com.lavarapido.booking.domain.service.BookingPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

// implementación del caso de uso para crear reservas
@Service
public class CreateBookingService implements CreateBookingUseCase {

    private final BookingRepository bookingRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final AtomicLong codeSequence = new AtomicLong(1);

    public CreateBookingService(BookingRepository bookingRepository,
                                ServiceTypeRepository serviceTypeRepository) {
        this.bookingRepository = bookingRepository;
        this.serviceTypeRepository = serviceTypeRepository;
    }

    @Override
    @Transactional
    public Booking createBooking(CreateBookingCommand command) {
        // validar que la fecha no sea pasada
        LocalDate date = LocalDate.parse(command.bookingDate());
        if (!BookingPolicy.isDateValid(date)) {
            throw new IllegalArgumentException("La fecha de la reserva no puede ser pasada");
        }

        // calcular el precio total según el tipo de servicio
        BigDecimal totalAmount = serviceTypeRepository.findById(command.serviceOfferingId())
                .map(st -> st.basePrice())
                .orElse(BigDecimal.ZERO);

        // crear la reserva con estado PENDING
        Booking booking = new Booking(
                null,
                BookingPolicy.generateBookingCode(codeSequence.getAndIncrement()),
                command.customerId(),
                command.vehicleId(),
                command.serviceOfferingId(),
                command.locationId(),
                date,
                LocalTime.parse(command.bookingTime()),
                BookingStatus.PENDING,
                totalAmount,
                command.notes()
        );

        return bookingRepository.save(booking);
    }
}
