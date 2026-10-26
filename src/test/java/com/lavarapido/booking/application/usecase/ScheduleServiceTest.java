package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.exception.ScheduleConflictException;
import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.model.ScheduleEntityType;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ScheduleService (horario, excepciones y bahías, F-05)")
class ScheduleServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");   // 10:00 en Bogotá
    private static final LocalDate TODAY = LocalDate.parse("2026-10-23");
    /** Jueves 29 de octubre de 2026. */
    private static final LocalDate THURSDAY = LocalDate.parse("2026-10-29");
    private static final long ADMIN = 1L;
    private static final short BAY_1 = 1;

    private final ScheduleRepository schedule = mock(ScheduleRepository.class);
    private final BayRepository bays = mock(BayRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ScheduleService service = new ScheduleService(schedule, bays, bookings,
            new BookingSettings(BOGOTA, 30, 60, 5), Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        // BIZ-10: por defecto, semana de lunes a sábado hasta las 17:00, sin excepciones ni reservas
        given(schedule.findBusinessHours()).willReturn(week(LocalTime.of(17, 0)));
        given(schedule.findExceptionsFrom(any())).willReturn(List.of());
        given(bookings.findActiveEndingAfter(NOW)).willReturn(List.of());
    }

    private static BusinessHour day(int day) {
        return new BusinessHour((short) day, true, LocalTime.of(8, 0), LocalTime.of(17, 0), null, null);
    }

    /** Lunes a sábado de 08:00 a la hora dada; domingo cerrado. */
    private static List<BusinessHour> week(LocalTime closes) {
        List<BusinessHour> days = new ArrayList<>();
        for (short day = 1; day <= 7; day++) {
            days.add(new BusinessHour(day, day != 7, LocalTime.of(8, 0), closes, null, null));
        }
        return days;
    }

    /** Reserva confirmada de 75 minutos en la bahía 1, a esa hora local de ese día. */
    private static Booking bookingAt(long id, LocalDate date, String localTime) {
        Instant start = date.atTime(LocalTime.parse(localTime)).atZone(BOGOTA).toInstant();
        return Booking.restore(id, 5L, BAY_1, start, start.plusSeconds(75 * 60), BookingStatus.CONFIRMED, 7L, null,
                0, BigDecimal.ZERO, null, List.of(), NOW);
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

    @Test
    @DisplayName("BIZ-10: cerrar más temprano un día que tiene reservas más tarde es 409 con cuántas son")
    void shorterHoursWithBookingsOutsideAreRejected() {
        // el jueves hay una reserva de 15:30 a 16:45; el horario nuevo cierra a las 16:00
        given(bookings.findActiveEndingAfter(NOW)).willReturn(List.of(bookingAt(9L, THURSDAY, "15:30"),
                bookingAt(10L, THURSDAY, "09:00")));

        ScheduleConflictException error = assertThrows(ScheduleConflictException.class,
                () -> service.updateBusinessHours(week(LocalTime.of(16, 0)), ADMIN));

        assertEquals("SCHEDULE_CONFLICTS_BOOKINGS", error.code());
        assertEquals(1, error.conflictingBookings());
        assertEquals(List.of("RES-000009"), error.bookingCodes());
        verify(schedule, never()).saveBusinessHours(any(), anyLong());
        verify(bookings).lockSchedule();
    }

    @Test
    @DisplayName("BIZ-10: un horario en el que siguen cabiendo las reservas se guarda")
    void hoursThatKeepTheBookingsInsideAreSaved() {
        given(bookings.findActiveEndingAfter(NOW)).willReturn(List.of(bookingAt(10L, THURSDAY, "09:00")));

        service.updateBusinessHours(week(LocalTime.of(16, 0)), ADMIN);

        verify(schedule).saveBusinessHours(any(), anyLong());
    }

    @Test
    @DisplayName("BIZ-10: un día con excepción no lo cambia el horario semanal")
    void aDayWithAnExceptionIsNotAffectedByTheWeek() {
        given(bookings.findActiveEndingAfter(NOW)).willReturn(List.of(bookingAt(9L, THURSDAY, "15:30")));
        given(schedule.findExceptionsFrom(any())).willReturn(List.of(
                new HoursException(3, THURSDAY, false, LocalTime.of(8, 0), LocalTime.of(18, 0), "Jornada extendida")));

        service.updateBusinessHours(week(LocalTime.of(16, 0)), ADMIN);

        verify(schedule).saveBusinessHours(any(), anyLong());
    }

    @Test
    @DisplayName("BIZ-10: cerrar un día por festivo con reservas ese día es 409")
    void closingADayWithBookingsIsRejected() {
        given(bookings.findActiveEndingAfter(NOW)).willReturn(List.of(bookingAt(9L, THURSDAY, "09:00")));
        given(schedule.findExceptionByDate(THURSDAY)).willReturn(Optional.empty());

        ScheduleConflictException error = assertThrows(ScheduleConflictException.class, () -> service.createException(
                new HoursException(0, THURSDAY, true, null, null, "Festivo"), ADMIN));

        assertEquals(1, error.conflictingBookings());
        verify(schedule, never()).saveException(any(), anyLong());
    }

    @Test
    @DisplayName("BIZ-10: quitar una jornada extendida deja por fuera las reservas de la hora extra")
    void deletingAnExtendedDayWithLateBookingsIsRejected() {
        // la excepción abría el jueves hasta las 19:00 y hay una reserva a las 17:30
        given(schedule.findException(3)).willReturn(Optional.of(
                new HoursException(3, THURSDAY, false, LocalTime.of(8, 0), LocalTime.of(19, 0), "Jornada extendida")));
        given(bookings.findActiveEndingAfter(NOW)).willReturn(List.of(bookingAt(9L, THURSDAY, "17:30")));

        assertThrows(ScheduleConflictException.class, () -> service.deleteException(3, ADMIN));
        verify(schedule, never()).deleteException(anyInt(), anyLong(), any());
    }

    @Test
    @DisplayName("BIZ-10: poner en mantenimiento una bahía con reservas por delante es 409; renombrarla no")
    void maintenanceOnABayWithBookingsIsRejected() {
        given(bays.findById(BAY_1)).willReturn(Optional.of(new ServiceBay(BAY_1, "BAY-01", "Bahia 1", BayStatus.ACTIVE)));
        given(bays.existsName(any(), any())).willReturn(false);
        given(bookings.findActiveEndingAfter(NOW)).willReturn(List.of(bookingAt(9L, THURSDAY, "09:00")));

        ScheduleConflictException error = assertThrows(ScheduleConflictException.class,
                () -> service.updateBay(BAY_1, "Bahia 1", BayStatus.MAINTENANCE, ADMIN));
        assertEquals(1, error.conflictingBookings());
        verify(bays, never()).update(BAY_1, "Bahia 1", BayStatus.MAINTENANCE, ADMIN);

        service.updateBay(BAY_1, "Bahia uno", BayStatus.ACTIVE, ADMIN);
        verify(bays).update(BAY_1, "Bahia uno", BayStatus.ACTIVE, ADMIN);
    }
}
