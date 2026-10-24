package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.model.ScheduleEntityType;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ScheduleService (horario, excepciones y bahías, F-05)")
class ScheduleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");   // 10:00 en Bogotá
    private static final LocalDate TODAY = LocalDate.parse("2026-10-23");
    private static final long ADMIN = 1L;

    private final ScheduleRepository schedule = mock(ScheduleRepository.class);
    private final BayRepository bays = mock(BayRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ScheduleService service = new ScheduleService(schedule, bays, bookings,
            new BookingSettings(ZoneId.of("America/Bogota"), 30, 60, 5), Clock.fixed(NOW, ZoneOffset.UTC));

    private static BusinessHour day(int day) {
        return new BusinessHour((short) day, true, LocalTime.of(8, 0), LocalTime.of(17, 0), null, null);
    }

    @Test
    @DisplayName("un día sin fila en la base se muestra cerrado: la semana siempre tiene 7 días")
    void missingDaysShowAsClosed() {
        given(schedule.findBusinessHours()).willReturn(List.of(day(1), day(2)));

        List<BusinessHour> week = service.businessHours();

        assertEquals(7, week.size());
        assertFalse(week.get(6).working());
    }

    @Test
    @DisplayName("el horario semanal exige los 7 días, una vez cada uno, y deja historial")
    void weekNeedsTheSevenDays() {
        assertThrows(InvalidValueException.class, () -> service.updateBusinessHours(List.of(day(1), day(1)), ADMIN));

        List<BusinessHour> week = IntStream.rangeClosed(1, 7).mapToObj(ScheduleServiceTest::day).toList();
        given(schedule.findBusinessHours()).willReturn(week);
        service.updateBusinessHours(week, ADMIN);

        verify(schedule).saveBusinessHours(week, ADMIN);
        verify(schedule).recordHistory(eq(ScheduleEntityType.WEEK), any(), any(), eq(ADMIN));
    }

    @Test
    @DisplayName("una excepción no puede ser en el pasado ni repetir una fecha")
    void exceptionsRejectPastAndTakenDates() {
        HoursException yesterday = new HoursException(0, TODAY.minusDays(1), true, null, null, "Festivo");
        assertThrows(InvalidValueException.class, () -> service.createException(yesterday, ADMIN));

        HoursException tomorrow = new HoursException(0, TODAY.plusDays(1), true, null, null, "Festivo");
        given(schedule.findExceptionByDate(TODAY.plusDays(1)))
                .willReturn(Optional.of(new HoursException(9, TODAY.plusDays(1), true, null, null, "Otro")));
        ConflictException taken = assertThrows(ConflictException.class, () -> service.createException(tomorrow, ADMIN));
        assertEquals("EXCEPTION_DATE_TAKEN", taken.code());
        verify(schedule, never()).saveException(any(), anyLong());
    }

    @Test
    @DisplayName("no se repite el nombre de una bahía y una nueva no nace en mantenimiento")
    void bayRules() {
        given(bays.existsName("Bahía 1", null)).willReturn(true);
        ConflictException taken = assertThrows(ConflictException.class,
                () -> service.createBay(" Bahía 1 ", BayStatus.ACTIVE, ADMIN));
        assertEquals("BAY_NAME_TAKEN", taken.code());

        given(bays.insert("Bahía 3", BayStatus.ACTIVE, ADMIN)).willReturn(new ServiceBay((short) 3, "B3", "Bahía 3", BayStatus.ACTIVE));
        assertEquals((short) 3, service.createBay("Bahía 3", null, ADMIN).id());
    }

    @Test
    @DisplayName("una bahía con reservas por delante no se borra")
    void bayWithUpcomingBookingsIsNotDeleted() {
        given(bays.findById((short) 2)).willReturn(Optional.of(new ServiceBay((short) 2, "B2", "Bahía 2", BayStatus.ACTIVE)));
        given(bookings.hasUpcomingOnBay((short) 2, NOW)).willReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.deleteBay((short) 2, ADMIN));

        assertEquals("BAY_HAS_BOOKINGS", error.code());
        verify(bays, never()).softDelete(any(Short.class), anyLong(), any());
    }
}
