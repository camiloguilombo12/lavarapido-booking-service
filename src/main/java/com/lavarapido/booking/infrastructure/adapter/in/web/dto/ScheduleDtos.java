package com.lavarapido.booking.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.Establishment;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.model.ScheduleHistoryEntry;
import com.lavarapido.booking.domain.model.ServiceBay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Contratos HTTP del horario, las excepciones, las bahias y la sede. Las horas van como "HH:mm". */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    /** Un dia de la semana: 1 = lunes ... 7 = domingo. */
    public record BusinessHourDto(
            @NotNull @Min(1) @Max(7) Short dayOfWeek,
            @NotNull Boolean working,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closesAt,
            @JsonFormat(pattern = "HH:mm") LocalTime breakStartsAt,
            @JsonFormat(pattern = "HH:mm") LocalTime breakEndsAt) {

        public static BusinessHourDto from(BusinessHour hour) {
            return new BusinessHourDto(hour.dayOfWeek(), hour.working(), hour.opensAt(), hour.closesAt(),
                    hour.breakStartsAt(), hour.breakEndsAt());
        }

        public BusinessHour toDomain() {
            return new BusinessHour(dayOfWeek, working, opensAt, closesAt, breakStartsAt, breakEndsAt);
        }
    }

    public record ExceptionRequest(
            @NotNull LocalDate date,
            @NotNull Boolean closed,
            @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
            @JsonFormat(pattern = "HH:mm") LocalTime closesAt,
            @NotBlank @Size(max = 120) String reason) {

        public HoursException toDomain() {
            return new HoursException(0, date, closed, opensAt, closesAt, reason);
        }
    }

    public record ExceptionResponse(
            int id,
            LocalDate date,
            boolean closed,
            @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
            @JsonFormat(pattern = "HH:mm") LocalTime closesAt,
            String reason) {

        public static ExceptionResponse from(HoursException exception) {
            return new ExceptionResponse(exception.id(), exception.date(), exception.closed(), exception.opensAt(),
                    exception.closesAt(), exception.reason());
        }
    }

    /** status: ACTIVE, MAINTENANCE o INACTIVE (ADR-010). */
    public record BayRequest(@NotBlank @Size(max = 60) String name, String status) {

        public BayStatus statusOrDefault() {
            return status == null || status.isBlank() ? BayStatus.ACTIVE : BayStatus.of(status);
        }
    }

    public record BayResponse(short id, String code, String name, String status) {

        public static BayResponse from(ServiceBay bay) {
            return bay == null ? null : new BayResponse(bay.id(), bay.code(), bay.name(), bay.status().name());
        }
    }

    public record HistoryEntryResponse(long id, String entityType, String title, String detail, Instant changedAt,
                                       Long changedBy) {

        public static HistoryEntryResponse from(ScheduleHistoryEntry entry) {
            return new HistoryEntryResponse(entry.id(), entry.entityType().name(), entry.title(), entry.detail(),
                    entry.changedAt(), entry.changedBy());
        }
    }

    public record EstablishmentResponse(String tradeName, String legalName, String taxId, String address,
                                        String phone, String email, String logoUrl) {

        public static EstablishmentResponse from(Establishment establishment) {
            return new EstablishmentResponse(establishment.tradeName(), establishment.legalName(),
                    establishment.taxId(), establishment.address(), establishment.phone(), establishment.email(),
                    establishment.logoUrl());
        }
    }
}
