package com.lavarapido.booking.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingLine;
import com.lavarapido.booking.domain.model.CancellationReason;
import com.lavarapido.booking.domain.model.VehicleSnapshot;
import com.lavarapido.booking.domain.port.in.BookingRequest;
import com.lavarapido.booking.domain.port.in.BookingView;
import com.lavarapido.booking.domain.port.in.DayAvailability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Contratos HTTP de las reservas. Fechas y horas van en la hora local del lavadero
 * (date "aaaa-mm-dd", time "HH:mm"); scheduledStart/End van ademas en UTC (ISO) para quien lo necesite.
 */
public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull Long vehicleId,
            @NotEmpty List<Integer> serviceIds,
            @NotNull LocalDate date,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
            @Size(max = 300) String notes) {

        public BookingRequest toRequest() {
            return new BookingRequest(vehicleId, serviceIds, date, time, notes);
        }
    }

    /** serviceIds vacio conserva los servicios que ya tenia. */
    public record RescheduleRequest(
            List<Integer> serviceIds,
            @NotNull LocalDate date,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
            @Size(max = 300) String notes) {

        public BookingRequest toRequest() {
            return new BookingRequest(null, serviceIds, date, time, notes);
        }
    }

    public record CancelRequest(String reasonCode) {
    }

    public record StatusRequest(@NotBlank String status, String reasonCode) {
    }

    public record AvailabilityResponse(LocalDate date, boolean open, int durationMinutes, List<SlotResponse> slots) {

        public static AvailabilityResponse from(DayAvailability availability) {
            return new AvailabilityResponse(availability.date(), availability.open(), availability.durationMinutes(),
                    availability.slots().stream()
                            .map(slot -> new SlotResponse(slot.time(), slot.available()))
                            .toList());
        }
    }

    public record SlotResponse(@JsonFormat(pattern = "HH:mm") LocalTime time, boolean available) {
    }

    public record ReasonResponse(String code, String name) {

        public static ReasonResponse from(CancellationReason reason) {
            return reason == null ? null : new ReasonResponse(reason.code(), reason.name());
        }
    }

    public record VehicleResponse(long id, String licensePlate, String licensePlateFormatted, String vehicleType,
                                  String vehicleTypeName, String brand, String model) {

        static VehicleResponse from(VehicleSnapshot vehicle) {
            return vehicle == null ? null : new VehicleResponse(vehicle.id(), vehicle.licensePlate(),
                    vehicle.licensePlateFormatted(), vehicle.vehicleType(), vehicle.vehicleTypeName(),
                    vehicle.brand(), vehicle.model());
        }
    }

    /** lineId = booking_service_id (operations-service ejecuta y califica por linea). */
    public record LineResponse(Long lineId, int serviceId, String code, String name, BigDecimal price,
                               short estimatedMinutes, short quantity) {

        static LineResponse from(BookingLine line) {
            return new LineResponse(line.lineId(), line.serviceId(), line.serviceCode(), line.serviceName(), line.price(),
                    line.estimatedMinutes(), line.quantity());
        }
    }

    /**
     * La reserva lista para pintar. changeable dice si quien la ve todavia la puede cambiar o
     * cancelar (lo decide el backend con RF-007). ownerUserId solo llega en las rutas del admin.
     */
    public record BookingResponse(
            long id,
            String code,
            String status,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            int durationMinutes,
            Instant scheduledStart,
            Instant scheduledEnd,
            ScheduleDtos.BayResponse bay,
            VehicleResponse vehicle,
            Long ownerUserId,
            long bookedBy,
            List<LineResponse> services,
            BigDecimal subtotal,
            BigDecimal pointsDiscountAmount,
            BigDecimal total,
            ReasonResponse cancellationReason,
            String notes,
            boolean changeable,
            Instant createdAt) {

        public static BookingResponse from(BookingView view, boolean includeOwner) {
            Booking booking = view.booking();
            return new BookingResponse(
                    booking.id(),
                    booking.code(),
                    booking.status().name(),
                    view.localStart().toLocalDate(),
                    view.localStart().toLocalTime(),
                    view.localEnd().toLocalTime(),
                    booking.durationMinutes(),
                    booking.scheduledStart(),
                    booking.scheduledEnd(),
                    ScheduleDtos.BayResponse.from(view.bay()),
                    VehicleResponse.from(view.vehicle()),
                    includeOwner && view.vehicle() != null ? view.vehicle().ownerUserId() : null,
                    booking.bookedBy(),
                    booking.lines().stream().map(LineResponse::from).toList(),
                    booking.subtotal(),
                    booking.pointsDiscountAmount(),
                    booking.total(),
                    ReasonResponse.from(booking.cancellationReason()),
                    booking.notes(),
                    view.changeable(),
                    booking.createdAt());
        }
    }
}
