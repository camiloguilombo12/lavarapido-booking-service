package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.event.BookingEvent;
import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.exception.NotFoundException;
import com.lavarapido.booking.domain.exception.SlotUnavailableException;
import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.UnavailableReason;
import com.lavarapido.booking.domain.model.CancellationReason;
import com.lavarapido.booking.domain.model.CatalogItem;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.model.ServiceCategory;
import com.lavarapido.booking.domain.model.ServicePrice;
import com.lavarapido.booking.domain.model.VehicleSnapshot;
import com.lavarapido.booking.domain.port.in.BookingRequest;
import com.lavarapido.booking.domain.port.in.BookingView;
import com.lavarapido.booking.domain.port.in.Caller;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.port.out.CatalogRepository;
import com.lavarapido.booking.domain.port.out.CustomerDirectory;
import com.lavarapido.booking.domain.port.out.DomainEventPublisher;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Reservas (casos de uso)")
class BookingServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** 30 de septiembre de 2026, 10:00 en Bogota. */
    private static final Instant NOW = Instant.parse("2026-09-30T15:00:00Z");
    private static final LocalDate TOMORROW = LocalDate.parse("2026-10-01");
    private static final short SUV = 3;
    private static final Caller CLIENT = new Caller(7L, false);
    private static final Caller ADMIN = new Caller(1L, true);
    private static final VehicleSnapshot MY_SUV =
            new VehicleSnapshot(5L, "ABC123", "ABC-123", "SUV", SUV, "Camioneta SUV", "Mazda", "CX-5", null);
    private static final CatalogItem PREMIUM = new CatalogItem(2, "PREMIUM", "Premium", null,
            new ServiceCategory((short) 1, "LAVADO", "Lavado", (short) 1), true,
            List.of(new ServicePrice(30L, 2, SUV, new BigDecimal("40000"), (short) 75, LocalDate.parse("2026-01-01"), null)),
            0);

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final CatalogRepository catalog = mock(CatalogRepository.class);
    private final ScheduleRepository schedule = mock(ScheduleRepository.class);
    private final BayRepository bays = mock(BayRepository.class);
    private final CustomerDirectory customers = mock(CustomerDirectory.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, catalog, schedule, bays, customers, events,
                new BookingSettings(BOGOTA, 30, 60, 5), Clock.fixed(NOW, ZoneOffset.UTC));
        given(schedule.findBusinessHours()).willReturn(List.of(
                new BusinessHour((short) 4, true, LocalTime.of(8, 0), LocalTime.of(17, 0), null, null)));
        given(schedule.findExceptionByDate(any())).willReturn(Optional.empty());
        given(bays.findAll()).willReturn(List.of(new ServiceBay((short) 1, "BAY-01", "Bahia 1", BayStatus.ACTIVE),
                new ServiceBay((short) 2, "BAY-02", "Bahia 2", BayStatus.MAINTENANCE)));
        given(catalog.findServicesByIds(List.of(2))).willReturn(List.of(PREMIUM));
        given(customers.ownVehicle(5L)).willReturn(Optional.of(MY_SUV));
        given(bookings.findOccupying(any(), any())).willReturn(List.of());
        given(bookings.save(any(), anyLong())).willAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            if (booking.id() == 0L) {
                booking.assignId(99L);
            }
            return booking;
        });
    }

    private static BookingRequest at(String time) {
        return new BookingRequest(5L, List.of(2), TOMORROW, LocalTime.parse(time), null);
    }

    private static Booking existing(long id, long vehicleId, String startUtc) {
        Instant start = Instant.parse(startUtc);
        return Booking.restore(id, vehicleId, (short) 1, start, start.plusSeconds(75 * 60), BookingStatus.CONFIRMED,
                8L, null, 0, BigDecimal.ZERO, null, List.of(), NOW);
    }

    @Test
    @DisplayName("con bahia libre queda confirmada, con el precio del tipo de vehiculo y el evento")
    void createsAConfirmedBooking() {
        BookingView view = service.create(at("09:00"), CLIENT);

        assertEquals(BookingStatus.CONFIRMED, view.booking().status());
        assertEquals((short) 1, view.booking().serviceBayId());
        assertEquals(new BigDecimal("40000"), view.booking().total());
        assertEquals(Instant.parse("2026-10-01T14:00:00Z"), view.booking().scheduledStart());
        assertEquals(LocalTime.of(10, 15), view.localEnd().toLocalTime());

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals(BookingEvent.Type.CONFIRMED, event.getValue().type());
        assertEquals(7L, event.getValue().customerUserId());
        assertEquals("RES-000099", event.getValue().bookingCode());
        verify(bookings).lockSchedule();
    }

    @Test
    @DisplayName("RF-006: sin bahia libre no guarda nada y devuelve alternativas")
    void fullSlotGivesAlternatives() {
        given(bookings.findOccupying(any(), any())).willReturn(List.of(existing(1L, 6L, "2026-10-01T14:00:00Z")));

        SlotUnavailableException error = assertThrows(SlotUnavailableException.class,
                () -> service.create(at("09:00"), CLIENT));

        // la bahia 1 esta ocupada de 09:00 a 10:15 y la 2 en mantenimiento: la primera libre es 10:30
        assertEquals(5, error.alternatives().size());
        assertEquals(LocalTime.of(10, 30), error.alternatives().getFirst());
        assertEquals(UnavailableReason.NO_FREE_BAY, error.reason());
        verify(bookings, never()).save(any(), anyLong());
    }

    @Test
    @DisplayName("RF-006: una hora antes de abrir dice que está fuera del horario, no que no hay bahía")
    void timeBeforeOpeningIsOutsideHours() {
        SlotUnavailableException error = assertThrows(SlotUnavailableException.class,
                () -> service.create(at("06:00"), CLIENT));

        assertEquals(UnavailableReason.OUTSIDE_HOURS, error.reason());
        assertEquals("SLOT_UNAVAILABLE", error.code());
        assertEquals(LocalTime.of(8, 0), error.alternatives().getFirst());
        verify(bookings, never()).save(any(), anyLong());
    }

    @Test
    @DisplayName("INV-BOOK-002: un vehiculo que no es del cliente es 404")
    void foreignVehicleIsNotFound() {
        given(customers.ownVehicle(5L)).willReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.create(at("09:00"), CLIENT));
        verify(bookings, never()).save(any(), anyLong());
    }

    @Test
    @DisplayName("el mismo vehiculo no puede tener dos reservas que se crucen")
    void vehicleCannotBeTwiceAtTheSameTime() {
        // la otra reserva esta en la bahia 1, pero no importa: el vehiculo es el mismo
        given(bays.findAll()).willReturn(List.of(new ServiceBay((short) 1, "BAY-01", "Bahia 1", BayStatus.ACTIVE),
                new ServiceBay((short) 2, "BAY-02", "Bahia 2", BayStatus.ACTIVE)));
        given(bookings.findOccupying(any(), any())).willReturn(List.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));

        ConflictException error = assertThrows(ConflictException.class, () -> service.create(at("09:00"), CLIENT));
        assertEquals("VEHICLE_ALREADY_BOOKED", error.code());
    }

    @Test
    @DisplayName("un servicio sin tarifa para ese tipo de vehiculo no se puede reservar")
    void serviceWithoutPriceForTheVehicle() {
        given(customers.ownVehicle(5L)).willReturn(Optional.of(new VehicleSnapshot(5L, "ABC12D", "ABC-12D", "MOTO",
                (short) 6, "Moto", null, null, null)));

        ConflictException error = assertThrows(ConflictException.class, () -> service.create(at("09:00"), CLIENT));
        assertEquals("SERVICE_NOT_AVAILABLE_FOR_VEHICLE", error.code());
    }

    @Test
    @DisplayName("no se reserva en el pasado ni mas alla del limite de dias")
    void dateRange() {
        assertThrows(InvalidValueException.class, () -> service.create(
                new BookingRequest(5L, List.of(2), LocalDate.parse("2026-09-29"), LocalTime.of(9, 0), null), CLIENT));
        assertThrows(InvalidValueException.class, () -> service.create(
                new BookingRequest(5L, List.of(2), LocalDate.parse("2026-12-30"), LocalTime.of(9, 0), null), CLIENT));
    }

    @Test
    @DisplayName("otro cliente no ve ni cancela la reserva (404)")
    void othersCannotSeeTheBooking() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));
        given(customers.ownVehicles()).willReturn(List.of());

        assertThrows(NotFoundException.class, () -> service.cancel(1L, null, new Caller(20L, false)));
    }

    @Test
    @DisplayName("el cliente cancela con CUSTOMER_REQUEST y sale el evento")
    void customerCancels() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));
        given(customers.ownVehicles()).willReturn(List.of(MY_SUV));
        given(bookings.findCancellationReason("CUSTOMER_REQUEST")).willReturn(Optional.of(
                new CancellationReason((short) 1, "CUSTOMER_REQUEST", "El cliente la cancelo", false)));

        BookingView view = service.cancel(1L, null, CLIENT);

        assertEquals(BookingStatus.CANCELLED, view.booking().status());
        assertEquals("CUSTOMER_REQUEST", view.booking().cancellationReason().code());
        verify(events).publish(any());
    }

    @Test
    @DisplayName("A-20: el cliente cancela con el motivo que eligió, si es de su lado")
    void customerCancelsWithTheChosenReason() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));
        given(customers.ownVehicles()).willReturn(List.of(MY_SUV));
        given(bookings.findCancellationReason("SCHEDULE_CONFLICT")).willReturn(Optional.of(
                new CancellationReason((short) 2, "SCHEDULE_CONFLICT", "El cliente no puede a esa hora", false)));
        given(bookings.findCancellationReason("LATE_ARRIVAL")).willReturn(Optional.of(
                new CancellationReason((short) 5, "LATE_ARRIVAL", "El cliente llego tarde", true)));

        var error = assertThrows(InvalidValueException.class, () -> service.cancel(1L, "LATE_ARRIVAL", CLIENT));
        assertEquals("INVALID_CANCELLATION_REASON", error.code());

        BookingView view = service.cancel(1L, "SCHEDULE_CONFLICT", CLIENT);
        assertEquals("SCHEDULE_CONFLICT", view.booking().cancellationReason().code());
    }

    /** Reserva de mañana a las 09:00 (Bogotá) con una línea Premium de 40.000. */
    private static Booking premiumBooking() {
        Instant start = Instant.parse("2026-10-01T14:00:00Z");
        return Booking.restore(1L, 5L, (short) 1, start, start.plusSeconds(75 * 60), BookingStatus.CONFIRMED, 7L,
                null, 0, BigDecimal.ZERO, null, List.of(new com.lavarapido.booking.domain.model.BookingLine(30L, 2,
                        "PREMIUM", "Premium", new BigDecimal("40000"), (short) 75, (short) 1, 10L, 0)), NOW);
    }

    @Test
    @DisplayName("BIZ-01: el cliente no cambia los servicios al reprogramar (409 BOOKING_SERVICES_LOCKED)")
    void customerCannotChangeTheServicesWhenRescheduling() {
        given(bookings.findById(1L)).willReturn(Optional.of(premiumBooking()));

        ConflictException error = assertThrows(ConflictException.class, () -> service.reschedule(1L,
                new BookingRequest(null, List.of(1), TOMORROW, LocalTime.of(11, 0), null), CLIENT));

        assertEquals("BOOKING_SERVICES_LOCKED", error.code());
        verify(bookings, never()).save(any(), anyLong());
    }

    @Test
    @DisplayName("BIZ-01: el cliente sí mueve fecha y hora, con o sin repetir los mismos servicios")
    void customerMovesOnlyTheDateAndTime() {
        given(bookings.findById(1L)).willReturn(Optional.of(premiumBooking()));

        BookingView sameServices = service.reschedule(1L,
                new BookingRequest(null, List.of(2), TOMORROW, LocalTime.of(11, 0), null), CLIENT);
        BookingView noServices = service.reschedule(1L,
                new BookingRequest(null, null, TOMORROW, LocalTime.of(12, 0), null), CLIENT);

        assertEquals(LocalTime.of(11, 0), sameServices.localStart().toLocalTime());
        assertEquals(LocalTime.of(12, 0), noServices.localStart().toLocalTime());
        assertEquals(List.of(2), noServices.booking().lines().stream()
                .map(com.lavarapido.booking.domain.model.BookingLine::serviceId).toList());
        assertEquals(new BigDecimal("40000"), noServices.booking().total());
    }

    @Test
    @DisplayName("BIZ-01: el admin sí puede cambiar los servicios de la reserva")
    void adminCanChangeTheServices() {
        CatalogItem basic = new CatalogItem(1, "BASIC", "Basico", null,
                new ServiceCategory((short) 1, "LAVADO", "Lavado", (short) 1), true,
                List.of(new ServicePrice(31L, 1, SUV, new BigDecimal("23000"), (short) 45, LocalDate.parse("2026-01-01"), null)),
                0);
        given(catalog.findServicesByIds(List.of(1))).willReturn(List.of(basic));
        given(bookings.findById(1L)).willReturn(Optional.of(premiumBooking()));
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of(5L, new VehicleSnapshot(5L, "ABC123", "ABC-123",
                "SUV", SUV, "Camioneta SUV", "Mazda", "CX-5", 7L)));

        BookingView view = service.reschedule(1L,
                new BookingRequest(null, List.of(1), TOMORROW, LocalTime.of(11, 0), null), ADMIN);

        assertEquals(new BigDecimal("23000"), view.booking().total());
    }

    @Test
    @DisplayName("A-11: cuando el admin cambia el estado, sale el evento para avisarle al cliente")
    void adminStatusChangeNotifiesTheCustomer() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of(5L, new VehicleSnapshot(5L, "ABC123", "ABC-123",
                "SUV", SUV, "Camioneta SUV", "Mazda", "CX-5", 7L)));

        service.changeStatus(1L, BookingStatus.IN_PROGRESS, null, ADMIN);

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals(BookingEvent.Type.STATUS_CHANGED, event.getValue().type());
        assertEquals("IN_PROGRESS", event.getValue().status());
        assertEquals(7L, event.getValue().customerUserId());
    }

    @Test
    @DisplayName("A-11: el avance del operario no publica este evento (operations ya avisa)")
    void operatorAdvanceDoesNotPublishTheStatusEvent() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-09-30T15:10:00Z")));
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of());

        service.operatorAdvance(1L, BookingStatus.IN_PROGRESS, new Caller(30L, false));

        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("el admin ve el dueño del vehiculo en su listado")
    void adminListHasOwners() {
        given(bookings.findStartingBetween(any(), any(), any())).willReturn(List.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of(5L, new VehicleSnapshot(5L, "ABC123", "ABC-123",
                "SUV", SUV, "Camioneta SUV", "Mazda", "CX-5", 7L)));

        List<BookingView> views = service.list(TOMORROW, TOMORROW, null, ADMIN);

        assertEquals(7L, views.getFirst().vehicle().ownerUserId());
        assertThrows(NotFoundException.class, () -> service.list(TOMORROW, TOMORROW, null, CLIENT));
    }

    @Test
    @DisplayName("TEMPORAL: el operario ve las reservas del dia sin las canceladas")
    void operatorSeesTheDay() {
        Booking cancelled = existing(2L, 5L, "2026-10-01T16:00:00Z");
        cancelled.cancel(new CancellationReason((short) 1, "CUSTOMER_REQUEST", "El cliente la cancelo", false));
        given(bookings.findStartingBetween(any(), any(), any()))
                .willReturn(List.of(existing(1L, 5L, "2026-10-01T14:00:00Z"), cancelled));
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of(5L, new VehicleSnapshot(5L, "ABC123", "ABC-123",
                "SUV", SUV, "Camioneta SUV", "Mazda", "CX-5", 7L)));

        List<BookingView> views = service.operatorDay(TOMORROW, null, new Caller(30L, false));

        assertEquals(1, views.size());
        assertEquals(1L, views.getFirst().booking().id());
    }

    @Test
    @DisplayName("TEMPORAL: el operario empieza una reserva pero no puede marcar no-show")
    void operatorOnlyMovesForward() {
        // a las 15:00Z, una reserva de las 15:10Z ya se puede iniciar (15 minutos antes)
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-09-30T15:10:00Z")));
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of());

        BookingView view = service.operatorAdvance(1L, BookingStatus.IN_PROGRESS, new Caller(30L, false));

        assertEquals(BookingStatus.IN_PROGRESS, view.booking().status());
        assertThrows(InvalidValueException.class,
                () -> service.operatorAdvance(1L, BookingStatus.NO_SHOW, new Caller(30L, false)));
    }

    @Test
    @DisplayName("E-01: el operario no inicia una reserva de otro dia u otra hora")
    void operatorCannotStartTooEarly() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));

        var error = assertThrows(com.lavarapido.booking.domain.exception.ConflictException.class,
                () -> service.operatorAdvance(1L, BookingStatus.IN_PROGRESS, new Caller(30L, false)));

        assertEquals("SERVICE_TOO_EARLY", error.code());
        verify(bookings, never()).save(any(), anyLong());
    }

    @Test
    void daySummaryComparesTheShopDayWithTheDayBefore() {
        // 30/09 en Bogotá va de 05:00Z a 05:00Z del día siguiente
        given(bookings.findStartingBetween(Instant.parse("2026-09-30T05:00:00Z"), Instant.parse("2026-10-01T05:00:00Z"), null))
                .willReturn(List.of(mock(com.lavarapido.booking.domain.model.Booking.class)));
        given(bookings.findStartingBetween(Instant.parse("2026-09-29T05:00:00Z"), Instant.parse("2026-09-30T05:00:00Z"), null))
                .willReturn(List.of(mock(com.lavarapido.booking.domain.model.Booking.class),
                        mock(com.lavarapido.booking.domain.model.Booking.class), mock(com.lavarapido.booking.domain.model.Booking.class)));

        var summary = service.daySummary(null, ADMIN);

        assertEquals(LocalDate.parse("2026-09-30"), summary.date());
        assertEquals(1, summary.bookings());
        assertEquals(3, summary.previousDayBookings());
        assertEquals(-2, summary.difference());
        assertThrows(com.lavarapido.booking.domain.exception.DomainException.class, () -> service.daySummary(null, CLIENT));
    }

    @Test
    @DisplayName("el cierre automatico cancela las programadas vencidas y no toca las confirmadas")
    void closesExpiredBookings() {
        // terminaron a las 08:15 y 08:30 de hoy en Bogota; ahora son las 10:00 (margen de 60 min)
        Booking confirmed = existing(1L, 5L, "2026-09-30T12:00:00Z");
        Booking scheduled = Booking.restore(2L, 6L, null, Instant.parse("2026-09-30T12:15:00Z"),
                Instant.parse("2026-09-30T13:30:00Z"), BookingStatus.SCHEDULED, 8L, null, 0, BigDecimal.ZERO, null,
                List.of(), NOW);
        given(bookings.findNotStartedEndingBefore(Instant.parse("2026-09-30T14:00:00Z")))
                .willReturn(List.of(confirmed, scheduled));
        given(bookings.findCancellationReason("EXPIRED"))
                .willReturn(Optional.of(new CancellationReason((short) 7, "EXPIRED", "Vencio sin confirmarse", false)));

        assertEquals(1, service.closeExpired());

        // la confirmada la decide operations: pudo atenderse sin que nadie la registrara
        assertEquals(BookingStatus.CONFIRMED, confirmed.status());
        assertEquals(BookingStatus.CANCELLED, scheduled.status());
        verify(bookings).lockSchedule();
        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals(BookingEvent.Type.CANCELLED, event.getValue().type());
        assertEquals(2L, event.getValue().bookingId());
    }

    @Test
    @DisplayName("operations cierra como no asistio una confirmada vencida, nunca una que aun no vence")
    void marksAnUnattendedBookingOnlyWhenExpired() {
        // termino a las 08:15 de hoy en Bogota; ahora son las 10:00 (margen de 60 min)
        Booking ended = existing(1L, 5L, "2026-09-30T12:00:00Z");
        Booking later = existing(2L, 5L, "2026-09-30T14:30:00Z");
        given(bookings.findById(1L)).willReturn(Optional.of(ended));
        given(bookings.findById(2L)).willReturn(Optional.of(later));

        assertEquals(BookingStatus.NO_SHOW, service.markUnattended(1L).booking().status());
        assertThrows(com.lavarapido.booking.domain.exception.ConflictException.class, () -> service.markUnattended(2L));
        assertEquals(BookingStatus.CONFIRMED, later.status());
        verify(bookings).save(ended, 0L);
    }

    @Test
    @DisplayName("sin reservas vencidas no cambia nada, y el motivo del sistema no se ofrece al cancelar")
    void nothingToCloseAndTheSystemReasonIsHidden() {
        given(bookings.findNotStartedEndingBefore(any())).willReturn(List.of());
        given(bookings.findCancellationReasons()).willReturn(List.of(
                new CancellationReason((short) 1, "CUSTOMER_REQUEST", "El cliente la cancelo", false),
                new CancellationReason((short) 7, "EXPIRED", "Vencio sin confirmarse", false)));

        assertEquals(0, service.closeExpired());
        verify(bookings, never()).save(any(), anyLong());
        assertEquals(List.of("CUSTOMER_REQUEST"),
                service.cancellationReasons().stream().map(CancellationReason::code).toList());
    }
}
