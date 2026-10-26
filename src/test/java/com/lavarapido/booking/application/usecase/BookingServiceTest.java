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
import com.lavarapido.booking.domain.model.PaymentMode;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.model.ServiceCategory;
import com.lavarapido.booking.domain.model.ServicePrice;
import com.lavarapido.booking.domain.model.VehicleSnapshot;
import com.lavarapido.booking.domain.port.in.BookingRequest;
import com.lavarapido.booking.domain.port.in.BookingView;
import com.lavarapido.booking.domain.port.in.Caller;
import com.lavarapido.booking.domain.port.in.PaymentReportOutcome;
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
import java.time.Duration;
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
        return paying(time, "DIGITAL");
    }

    private static BookingRequest paying(String time, String paymentMode) {
        return new BookingRequest(5L, List.of(2), TOMORROW, LocalTime.parse(time), null, null, paymentMode);
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
                new BookingRequest(5L, List.of(2), LocalDate.parse("2026-09-29"), LocalTime.of(9, 0), null, null, "DIGITAL"), CLIENT));
        assertThrows(InvalidValueException.class, () -> service.create(
                new BookingRequest(5L, List.of(2), LocalDate.parse("2026-12-30"), LocalTime.of(9, 0), null, null, "DIGITAL"), CLIENT));
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

    @Test
    @DisplayName("A-20 / OPS-10: BookingCancelled lleva el código del motivo además de su texto")
    void cancelledEventCarriesTheReasonCode() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-10-01T14:00:00Z")));
        given(customers.ownVehicles()).willReturn(List.of(MY_SUV));
        given(bookings.findCancellationReason("SCHEDULE_CONFLICT")).willReturn(Optional.of(
                new CancellationReason((short) 2, "SCHEDULE_CONFLICT", "El cliente no puede a esa hora", false)));

        service.cancel(1L, "SCHEDULE_CONFLICT", CLIENT);

        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals(BookingEvent.Type.CANCELLED, event.getValue().type());
        assertEquals("SCHEDULE_CONFLICT", event.getValue().reasonCode());
        assertEquals("El cliente no puede a esa hora", event.getValue().reason());
    }

    /** Reserva de mañana a las 09:00 (Bogotá) con una línea Premium de 40.000. */
    private static Booking premiumBooking() {
        Instant start = Instant.parse("2026-10-01T14:00:00Z");
        return Booking.restore(1L, 5L, (short) 1, start, start.plusSeconds(75 * 60), BookingStatus.CONFIRMED, 7L,
                null, 0, BigDecimal.ZERO, null, List.of(new com.lavarapido.booking.domain.model.BookingLine(30L, 2,
                        "PREMIUM", "Premium", new BigDecimal("40000"), (short) 75, (short) 1, 10L, 0, SUV)), NOW);
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

    /** Reserva de mañana a las 09:00 con una línea Premium de la tarifa dada. */
    private static Booking bookingWith(long servicePriceId, String price, short vehicleTypeId) {
        Instant start = Instant.parse("2026-10-01T14:00:00Z");
        return Booking.restore(1L, 5L, (short) 1, start, start.plusSeconds(75 * 60), BookingStatus.CONFIRMED, 7L,
                null, 0, BigDecimal.ZERO, null, List.of(new com.lavarapido.booking.domain.model.BookingLine(
                        servicePriceId, 2, "PREMIUM", "Premium", new BigDecimal(price), (short) 75, (short) 1, 10L, 0,
                        vehicleTypeId)), NOW);
    }

    @Test
    @DisplayName("INV-BOOK-008: al reprogramar se conserva la tarifa congelada si el vehículo es del mismo tipo")
    void reschedulingKeepsTheFrozenPriceForTheSameVehicleType() {
        // la reserva se hizo a 35.000 (tarifa 29); hoy la Premium para SUV vale 40.000 (tarifa 30)
        given(bookings.findById(1L)).willReturn(Optional.of(bookingWith(29L, "35000", SUV)));

        BookingView view = service.reschedule(1L,
                new BookingRequest(null, null, TOMORROW, LocalTime.of(11, 0), null), CLIENT);

        assertEquals(new BigDecimal("35000"), view.booking().total());
        assertEquals(29L, view.booking().lines().getFirst().servicePriceId());
    }

    @Test
    @DisplayName("BIZ-06: si el vehículo cambió de tipo, al reprogramar va la tarifa vigente del tipo nuevo")
    void reschedulingUsesTheNewVehicleTypePrice() {
        // se reservó como moto (tipo 6, 28.000); el cliente cambió el vehículo a SUV
        given(bookings.findById(1L)).willReturn(Optional.of(bookingWith(40L, "28000", (short) 6)));

        BookingView view = service.reschedule(1L,
                new BookingRequest(null, null, TOMORROW, LocalTime.of(11, 0), null), CLIENT);

        assertEquals(new BigDecimal("40000"), view.booking().total());
        assertEquals(30L, view.booking().lines().getFirst().servicePriceId());
        assertEquals(SUV, view.booking().lines().getFirst().vehicleTypeId());
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
    @DisplayName("BIZ-08: cuenta las reservas activas del vehículo a la hora actual")
    void countsTheActiveBookingsOfAVehicle() {
        given(bookings.countActiveForVehicle(5L, NOW)).willReturn(2L);

        assertEquals(2L, service.activeBookingsOf(5L));
        assertThrows(InvalidValueException.class, () -> service.activeBookingsOf(0L));
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
    @DisplayName("E-11: el avance del operario, el cambio de estado y la cancelación toman el candado de la agenda")
    void statusChangesTakeTheScheduleLock() {
        given(bookings.findById(1L)).willReturn(Optional.of(existing(1L, 5L, "2026-09-30T15:10:00Z")));
        given(bookings.findById(2L)).willReturn(Optional.of(existing(2L, 5L, "2026-10-01T14:00:00Z")));
        given(bookings.findById(3L)).willReturn(Optional.of(existing(3L, 5L, "2026-10-01T16:00:00Z")));
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of());
        given(bookings.findCancellationReason("SHOP_ISSUE")).willReturn(Optional.of(
                new CancellationReason((short) 4, "SHOP_ISSUE", "Problema del lavadero", false)));

        service.operatorAdvance(1L, BookingStatus.IN_PROGRESS, new Caller(30L, false));
        service.changeStatus(3L, BookingStatus.NO_SHOW, null, ADMIN);
        service.changeStatus(2L, BookingStatus.CANCELLED, "SHOP_ISSUE", ADMIN);

        // operario y estado: se bloquea antes de leer; cancelar: después de validar y antes de guardar
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(bookings);
        order.verify(bookings).lockSchedule();
        order.verify(bookings).findById(1L);
        order.verify(bookings).lockSchedule();
        order.verify(bookings).findById(3L);
        order.verify(bookings).findById(2L);
        order.verify(bookings).lockSchedule();
        order.verify(bookings).save(org.mockito.ArgumentMatchers.argThat(booking -> booking.id() == 2L), anyLong());
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

    // ------------------------------------------------------------------ contrato de pago (B y D)

    @Test
    @DisplayName("contrato de pago: el cliente sin modo de pago, o con uno que no existe, recibe 400 INVALID_PAYMENT_MODE")
    void customerMustChooseAPaymentMode() {
        InvalidValueException missing = assertThrows(InvalidValueException.class,
                () -> service.create(paying("09:00", null), CLIENT));
        InvalidValueException unknown = assertThrows(InvalidValueException.class,
                () -> service.create(paying("09:00", "CARD"), CLIENT));

        assertEquals("INVALID_PAYMENT_MODE", missing.code());
        assertEquals("INVALID_PAYMENT_MODE", unknown.code());
        verify(bookings, never()).save(any(), anyLong());
    }

    @Test
    @DisplayName("D: la reserva DIGITAL del cliente queda con plazo = creación + 30 min (empieza mucho después)")
    void digitalBookingGetsADeadline() {
        BookingView view = service.create(at("09:00"), CLIENT);

        assertEquals(PaymentMode.DIGITAL, view.booking().paymentMode());
        assertEquals(NOW.plus(Duration.ofMinutes(30)), view.booking().paymentDeadline());
        assertEquals(false, view.booking().paymentReported());
        // la DIGITAL no cuenta el efectivo
        verify(bookings, never()).countActiveCashBookedBy(anyLong());
    }

    @Test
    @DisplayName("D: si la reserva empieza antes de que se acabe la ventana, el plazo es la hora de inicio")
    void digitalDeadlineIsCappedAtTheStart() {
        // hoy a las 10:00 en Bogota; la ventana es de 2 horas y la reserva es a las 11:00
        service = new BookingService(bookings, catalog, schedule, bays, customers, events,
                new BookingSettings(BOGOTA, 30, 60, 5, Duration.ofMinutes(60), 1, Duration.ofHours(2)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        given(schedule.findBusinessHours()).willReturn(List.of(
                new BusinessHour((short) 3, true, LocalTime.of(8, 0), LocalTime.of(17, 0), null, null)));

        BookingView view = service.create(new BookingRequest(5L, List.of(2), LocalDate.parse("2026-09-30"),
                LocalTime.of(11, 0), null, null, "DIGITAL"), CLIENT);

        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), view.booking().paymentDeadline());
    }

    @Test
    @DisplayName("B: con la agenda bloqueada, el efectivo pasa por debajo del límite y no tiene plazo")
    void cashBelowTheLimit() {
        given(bookings.countActiveCashBookedBy(7L)).willReturn(0L);

        BookingView view = service.create(paying("09:00", "CASH"), CLIENT);

        assertEquals(PaymentMode.CASH, view.booking().paymentMode());
        assertEquals(null, view.booking().paymentDeadline());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(bookings);
        order.verify(bookings).lockSchedule();
        order.verify(bookings).countActiveCashBookedBy(7L);
        order.verify(bookings).save(any(), anyLong());
    }

    @Test
    @DisplayName("B: el cliente que ya llegó al límite de efectivo recibe 409 CASH_BOOKING_LIMIT y no se guarda nada")
    void cashAtTheLimit() {
        given(bookings.countActiveCashBookedBy(7L)).willReturn(1L);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.create(paying("09:00", "CASH"), CLIENT));

        assertEquals("CASH_BOOKING_LIMIT", error.code());
        verify(bookings, never()).save(any(), anyLong());
        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("regla 5: el admin no tiene límite de efectivo ni plazo, y el modo es opcional")
    void adminIsExempt() {
        given(customers.vehiclesByIds(List.of(5L))).willReturn(Map.of(5L, MY_SUV));
        given(bookings.countActiveCashBookedBy(anyLong())).willReturn(5L);

        BookingView cash = service.create(paying("09:00", "CASH"), ADMIN);
        assertEquals(PaymentMode.CASH, cash.booking().paymentMode());
        assertEquals(null, cash.booking().paymentDeadline());

        BookingView digital = service.create(paying("11:00", "DIGITAL"), ADMIN);
        assertEquals(PaymentMode.DIGITAL, digital.booking().paymentMode());
        assertEquals(null, digital.booking().paymentDeadline());

        BookingView none = service.create(paying("13:00", null), ADMIN);
        assertEquals(null, none.booking().paymentMode());
        verify(bookings, never()).countActiveCashBookedBy(anyLong());

        // un valor que no existe sigue siendo 400, también para el admin
        assertThrows(InvalidValueException.class, () -> service.create(paying("15:00", "BITCOIN"), ADMIN));
    }

    private static final CancellationReason PAYMENT_TIMEOUT =
            new CancellationReason((short) 8, "PAYMENT_TIMEOUT", "No se subió el comprobante de pago a tiempo", false);

    /** DIGITAL de mañana con plazo a las 09:30 de hoy en Bogota (ya vencido a las 10:00). */
    private static Booking digital(long id, BookingStatus status, Instant reportedAt) {
        Instant start = Instant.parse("2026-10-01T14:00:00Z");
        return Booking.restore(id, 5L, (short) 1, start, start.plusSeconds(75 * 60), status, 7L, null, 0,
                BigDecimal.ZERO, null, List.of(), NOW.minusSeconds(3600), PaymentMode.DIGITAL,
                NOW.minusSeconds(1800), reportedAt);
    }

    @Test
    @DisplayName("D: el job recibe solo las vencidas que el dominio confirma")
    void overdueBookingsAreFilteredByTheDomain() {
        given(bookings.findPaymentOverdue(NOW)).willReturn(List.of(digital(1L, BookingStatus.CONFIRMED, null),
                digital(2L, BookingStatus.CONFIRMED, NOW.minusSeconds(60))));

        assertEquals(List.of(1L), service.paymentOverdueBookings());
    }

    @Test
    @DisplayName("D: la vencida se cancela con PAYMENT_TIMEOUT por el camino normal y sale BookingCancelled")
    void paymentTimeoutCancelsThroughTheUsualPath() {
        Booking overdue = digital(1L, BookingStatus.CONFIRMED, null);
        given(bookings.findById(1L)).willReturn(Optional.of(overdue));
        given(bookings.findCancellationReason("PAYMENT_TIMEOUT")).willReturn(Optional.of(PAYMENT_TIMEOUT));

        assertEquals(true, service.cancelForPaymentTimeout(1L));

        assertEquals(BookingStatus.CANCELLED, overdue.status());
        assertEquals("PAYMENT_TIMEOUT", overdue.cancellationReason().code());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(bookings);
        order.verify(bookings).lockSchedule();
        order.verify(bookings).findById(1L);
        order.verify(bookings).save(overdue, 0L);
        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publish(event.capture());
        assertEquals(BookingEvent.Type.CANCELLED, event.getValue().type());
        assertEquals("PAYMENT_TIMEOUT", event.getValue().reasonCode());
        assertEquals("No se subió el comprobante de pago a tiempo", event.getValue().reason());
        // el cliente que la hizo recibe el aviso (sin token no se le pregunta a customer-service)
        assertEquals(7L, event.getValue().customerUserId());
        verify(customers, never()).vehiclesByIds(any());
    }

    @Test
    @DisplayName("D: si llegó el comprobante, ya no existe o ya se canceló, no se toca ni se avisa")
    void paymentTimeoutSkipsWhatIsNoLongerOverdue() {
        given(bookings.findById(1L)).willReturn(Optional.of(digital(1L, BookingStatus.CONFIRMED, NOW.minusSeconds(5))));
        given(bookings.findById(2L)).willReturn(Optional.empty());
        given(bookings.findById(3L)).willReturn(Optional.of(digital(3L, BookingStatus.CANCELLED, null)));

        assertEquals(false, service.cancelForPaymentTimeout(1L));
        assertEquals(false, service.cancelForPaymentTimeout(2L));
        assertEquals(false, service.cancelForPaymentTimeout(3L));
        verify(bookings, never()).save(any(), anyLong());
        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("el aviso de pago se guarda una vez; la repetida, la cancelada y la inexistente no cambian nada")
    void paymentReportIsIdempotent() {
        Booking pending = digital(1L, BookingStatus.CONFIRMED, null);
        given(bookings.findById(1L)).willReturn(Optional.of(pending));
        given(bookings.findById(2L)).willReturn(Optional.of(digital(2L, BookingStatus.CANCELLED, null)));
        given(bookings.findById(3L)).willReturn(Optional.empty());

        assertEquals(PaymentReportOutcome.RECORDED, service.recordPaymentReported(1L));
        assertEquals(NOW, pending.paymentReportedAt());
        assertEquals(PaymentReportOutcome.ALREADY_REPORTED, service.recordPaymentReported(1L));
        assertEquals(PaymentReportOutcome.CANCELLED, service.recordPaymentReported(2L));
        assertEquals(PaymentReportOutcome.NOT_FOUND, service.recordPaymentReported(3L));

        verify(bookings, org.mockito.Mockito.times(1)).save(any(), anyLong());
        verify(bookings, org.mockito.Mockito.times(4)).lockSchedule();
    }

    @Test
    @DisplayName("los motivos del sistema (EXPIRED, PAYMENT_TIMEOUT) no se ofrecen al cancelar")
    void theSystemReasonIsHidden() {
        given(bookings.findCancellationReasons()).willReturn(List.of(
                new CancellationReason((short) 1, "CUSTOMER_REQUEST", "El cliente la cancelo", false),
                new CancellationReason((short) 7, "EXPIRED", "Vencio sin confirmarse", false),
                new CancellationReason((short) 8, "PAYMENT_TIMEOUT", "No se subió el comprobante de pago a tiempo", false)));

        assertEquals(List.of("CUSTOMER_REQUEST"),
                service.cancellationReasons().stream().map(CancellationReason::code).toList());
    }
}
