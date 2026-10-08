package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.event.BookingEvent;
import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.exception.NotFoundException;
import com.lavarapido.booking.domain.exception.SlotUnavailableException;
import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingLine;
import com.lavarapido.booking.domain.model.BookingStatus;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.CancellationReason;
import com.lavarapido.booking.domain.model.CatalogItem;
import com.lavarapido.booking.domain.model.DayWindow;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.model.ServicePrice;
import com.lavarapido.booking.domain.model.VehicleSnapshot;
import com.lavarapido.booking.domain.port.in.BookingRequest;
import com.lavarapido.booking.domain.port.in.BookingUseCase;
import com.lavarapido.booking.domain.port.in.BookingView;
import com.lavarapido.booking.domain.port.in.Caller;
import com.lavarapido.booking.domain.port.in.DayAvailability;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.port.out.CatalogRepository;
import com.lavarapido.booking.domain.port.out.CustomerDirectory;
import com.lavarapido.booking.domain.port.out.DomainEventPublisher;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import com.lavarapido.booking.domain.service.AvailabilityPolicy;
import com.lavarapido.booking.domain.service.AvailabilityPolicy.Occupancy;
import com.lavarapido.booking.domain.service.AvailabilityPolicy.Slot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reservas del cliente y del administrador.
 *
 * Flujo de una reserva nueva (RF-006):
 *  1. el vehiculo se valida contra customer-service con el token de quien llama (INV-BOOK-002);
 *  2. el precio y la duracion salen de la tarifa vigente para el tipo de ese vehiculo, nunca del
 *     cliente (INV-BOOK-008);
 *  3. con la agenda bloqueada se busca una bahia libre en esa franja;
 *  4. si hay, la reserva queda CONFIRMED con esa bahia; si no, no se guarda nada y se responden
 *     hasta N horas libres del mismo dia.
 */
@Service
@Transactional
public class BookingService implements BookingUseCase {

    private static final short DEFAULT_QUANTITY = 1;
    private static final String CUSTOMER_CANCEL_REASON = "CUSTOMER_REQUEST";
    private static final long MAX_LIST_DAYS = 62;

    private final BookingRepository bookings;
    private final CatalogRepository catalog;
    private final ScheduleRepository schedule;
    private final BayRepository bays;
    private final CustomerDirectory customers;
    private final DomainEventPublisher events;
    private final BookingSettings settings;
    private final Clock clock;

    public BookingService(BookingRepository bookings, CatalogRepository catalog, ScheduleRepository schedule,
                          BayRepository bays, CustomerDirectory customers, DomainEventPublisher events,
                          BookingSettings settings, Clock clock) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.schedule = schedule;
        this.bays = bays;
        this.customers = customers;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ disponibilidad

    @Override
    @Transactional(readOnly = true)
    public DayAvailability availability(LocalDate date, short vehicleTypeId, List<Integer> serviceIds,
                                        Long excludeBookingId) {
        requireBookableDate(date);
        List<BookingLine> lines = linesFor(serviceIds, vehicleTypeId);
        int duration = lines.stream().mapToInt(BookingLine::minutes).sum();
        Optional<DayWindow> window = windowOf(date);
        List<Slot> slots = slotsOf(date, window, duration, excludeBookingId == null ? 0L : excludeBookingId);
        return new DayAvailability(date, window.isPresent(), duration, slots.stream()
                .map(slot -> new DayAvailability.TimeSlot(slot.time(), slot.available()))
                .toList());
    }

    // ------------------------------------------------------------------ crear y cambiar

    @Override
    public BookingView create(BookingRequest request, Caller caller) {
        if (request.vehicleId() == null) {
            throw new InvalidValueException("VEHICLE_REQUIRED", "Choose a vehicle");
        }
        VehicleSnapshot vehicle = requireVehicle(request.vehicleId(), caller);
        List<BookingLine> lines = linesFor(request.serviceIds(), vehicle.vehicleTypeId());
        Instant now = clock.instant();

        bookings.lockSchedule();
        Slot slot = requireFreeSlot(request.date(), request.time(), lines, 0L);
        LocalDateTime start = request.date().atTime(slot.time());
        requireVehicleFree(vehicle.id(), start, start.plusMinutes(durationOf(lines)), 0L);
        Booking booking = Booking.confirmNew(vehicle.id(), lines, toInstant(start),
                toInstant(start.plusMinutes(durationOf(lines))), slot.bayId(), caller.userId(), request.notes(), now);
        Booking saved = bookings.save(booking, caller.userId());

        publish(BookingEvent.Type.CONFIRMED, saved, customerUserOf(vehicle, caller), null);
        return view(saved, vehicle, caller);
    }

    @Override
    public BookingView reschedule(long bookingId, BookingRequest request, Caller caller) {
        Booking booking = requireVisible(bookingId, caller);
        requireChangeableBy(booking, caller);
        VehicleSnapshot vehicle = vehicleOf(booking, caller);
        if (vehicle == null) {
            throw new NotFoundException("VEHICLE_NOT_FOUND", "The vehicle of this booking no longer exists");
        }
        List<Integer> serviceIds = request.serviceIds() == null || request.serviceIds().isEmpty()
                ? booking.lines().stream().map(BookingLine::serviceId).toList()
                : request.serviceIds();
        List<BookingLine> lines = keepFrozenPrices(booking, linesFor(serviceIds, vehicle.vehicleTypeId()));

        bookings.lockSchedule();
        Slot slot = requireFreeSlot(request.date(), request.time(), lines, booking.id());
        LocalDateTime start = request.date().atTime(slot.time());
        requireVehicleFree(booking.customerVehicleId(), start, start.plusMinutes(durationOf(lines)), booking.id());
        booking.reschedule(lines, toInstant(start), toInstant(start.plusMinutes(durationOf(lines))), slot.bayId(),
                request.notes() == null ? booking.notes() : request.notes());
        Booking saved = bookings.save(booking, caller.userId());

        publish(BookingEvent.Type.CONFIRMED, saved, customerUserOf(vehicle, caller), null);
        return view(saved, vehicle, caller);
    }

    @Override
    public BookingView cancel(long bookingId, String reasonCode, Caller caller) {
        Booking booking = requireVisible(bookingId, caller);
        requireChangeableBy(booking, caller);
        String code = caller.admin() && reasonCode != null && !reasonCode.isBlank() ? reasonCode : CUSTOMER_CANCEL_REASON;
        booking.cancel(requireReason(code));
        Booking saved = bookings.save(booking, caller.userId());

        VehicleSnapshot vehicle = vehicleOf(saved, caller);
        publish(BookingEvent.Type.CANCELLED, saved, customerUserOf(vehicle, caller), saved.cancellationReason().name());
        return view(saved, vehicle, caller);
    }

    @Override
    public BookingView changeStatus(long bookingId, BookingStatus status, String reasonCode, Caller caller) {
        requireAdmin(caller);
        if (status == BookingStatus.CANCELLED) {
            return cancel(bookingId, reasonCode, caller);
        }
        Booking booking = requireBooking(bookingId);
        booking.moveTo(status);
        Booking saved = bookings.save(booking, caller.userId());
        return view(saved, vehicleOf(saved, caller), caller);
    }

    // ------------------------------------------------------------------ consultas

    @Override
    @Transactional(readOnly = true)
    public List<BookingView> mine(Caller caller) {
        List<VehicleSnapshot> vehicles = customers.ownVehicles();
        Map<Long, VehicleSnapshot> byId = vehicles.stream()
                .collect(Collectors.toMap(VehicleSnapshot::id, Function.identity(), (a, b) -> a));
        Map<Short, ServiceBay> bayById = baysById();
        return bookings.findForCustomer(caller.userId(), byId.keySet()).stream()
                .map(booking -> view(booking, byId.get(booking.customerVehicleId()), bayById, caller))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingView get(long bookingId, Caller caller) {
        Booking booking = requireVisible(bookingId, caller);
        return view(booking, vehicleOf(booking, caller), caller);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingView> list(LocalDate from, LocalDate to, BookingStatus status, Caller caller) {
        requireAdmin(caller);
        LocalDate first = from == null ? today() : from;
        LocalDate last = to == null ? first : to;
        if (last.isBefore(first) || ChronoUnit.DAYS.between(first, last) > MAX_LIST_DAYS) {
            throw new InvalidValueException("INVALID_RANGE", "The range must go forward and cover at most "
                    + MAX_LIST_DAYS + " days");
        }
        List<Booking> found = bookings.findStartingBetween(toInstant(first.atStartOfDay()),
                toInstant(last.plusDays(1).atStartOfDay()), status);
        Map<Long, VehicleSnapshot> vehicles = found.isEmpty() ? Map.of()
                : customers.vehiclesByIds(found.stream().map(Booking::customerVehicleId).distinct().toList());
        Map<Short, ServiceBay> bayById = baysById();
        return found.stream()
                .map(booking -> view(booking, vehicles.get(booking.customerVehicleId()), bayById, caller))
                .toList();
    }

    // ------------------------------------------------------------------ operario (TEMPORAL)
    // Mientras no exista operations-service, el operario trabaja sobre las reservas del dia.
    // El rol OPERATOR ya lo exigio SecurityConfig; aqui se le trata como personal del lavadero
    // (ve todas las reservas) pero solo puede mover el estado hacia adelante.

    @Override
    @Transactional(readOnly = true)
    public List<BookingView> operatorDay(LocalDate date, LocalDate to, Caller caller) {
        LocalDate day = date == null ? today() : date;
        return list(day, to == null ? day : to, null, staff(caller)).stream()
                .filter(view -> view.booking().status() != BookingStatus.CANCELLED)
                .toList();
    }

    @Override
    public BookingView operatorAdvance(long bookingId, BookingStatus status, Caller caller) {
        if (status != BookingStatus.IN_PROGRESS && status != BookingStatus.COMPLETED) {
            throw new InvalidValueException("INVALID_STATUS", "An operator can only start or complete a booking");
        }
        return changeStatus(bookingId, status, null, staff(caller));
    }

    private static Caller staff(Caller caller) {
        return new Caller(caller.userId(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CancellationReason> cancellationReasons() {
        return bookings.findCancellationReasons();
    }

    // ------------------------------------------------------------------ reglas internas

    /** Tarifas vigentes para ese tipo de vehiculo, en el orden pedido, sin repetidos. */
    private List<BookingLine> linesFor(List<Integer> serviceIds, short vehicleTypeId) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new InvalidValueException("SERVICES_REQUIRED", "Choose at least one service");
        }
        Set<Integer> ids = new LinkedHashSet<>(serviceIds);
        Map<Integer, CatalogItem> found = catalog.findServicesByIds(List.copyOf(ids)).stream()
                .collect(Collectors.toMap(CatalogItem::id, Function.identity()));
        List<BookingLine> lines = new ArrayList<>();
        for (Integer id : ids) {
            CatalogItem item = found.get(id);
            if (item == null || !item.active()) {
                throw new NotFoundException("SERVICE_NOT_FOUND", "Service " + id + " is not available");
            }
            ServicePrice price = item.priceFor(vehicleTypeId).orElseThrow(() -> new ConflictException(
                    "SERVICE_NOT_AVAILABLE_FOR_VEHICLE", "Service " + item.code() + " has no price for this vehicle type"));
            lines.add(new BookingLine(price.id(), item.id(), item.code(), item.name(), price.price(),
                    price.estimatedMinutes(), DEFAULT_QUANTITY, item.loyaltyPoints()));
        }
        return lines;
    }

    /**
     * Al reprogramar, los servicios que ya estaban conservan la tarifa con la que se reservaron
     * (INV-BOOK-008): subir un precio no le cambia el cobro a quien ya tenia su reserva.
     */
    private static List<BookingLine> keepFrozenPrices(Booking booking, List<BookingLine> current) {
        Map<Integer, BookingLine> frozen = booking.lines().stream()
                .collect(Collectors.toMap(BookingLine::serviceId, Function.identity(), (a, b) -> a));
        return current.stream().map(line -> frozen.getOrDefault(line.serviceId(), line)).toList();
    }

    private Slot requireFreeSlot(LocalDate date, LocalTime time, List<BookingLine> lines, long excludeBookingId) {
        requireBookableDate(date);
        if (time == null) {
            throw new InvalidValueException("TIME_REQUIRED", "Choose a time");
        }
        List<Slot> slots = slotsOf(date, windowOf(date), durationOf(lines), excludeBookingId);
        LocalTime requested = time.truncatedTo(ChronoUnit.MINUTES);
        return slots.stream()
                .filter(slot -> slot.time().equals(requested) && slot.available())
                .findFirst()
                .orElseThrow(() -> new SlotUnavailableException(
                        AvailabilityPolicy.alternatives(slots, requested, settings.alternatives())));
    }

    /** Un vehiculo no puede estar en dos reservas activas que se crucen (no puede estar en dos bahias). */
    private void requireVehicleFree(long vehicleId, LocalDateTime start, LocalDateTime end, long excludeBookingId) {
        boolean busy = bookings.findOccupying(toInstant(start), toInstant(end)).stream()
                .anyMatch(other -> other.id() != excludeBookingId && other.customerVehicleId() == vehicleId);
        if (busy) {
            throw new ConflictException("VEHICLE_ALREADY_BOOKED", "The vehicle already has a booking at that time");
        }
    }

    private List<Slot> slotsOf(LocalDate date, Optional<DayWindow> window, int duration, long excludeBookingId) {
        List<Short> activeBays = bays.findAll().stream()
                .filter(bay -> bay.status().acceptsBookings())
                .map(ServiceBay::id)
                .toList();
        List<Occupancy> taken = bookings.findOccupying(toInstant(date.atStartOfDay()),
                        toInstant(date.plusDays(1).atStartOfDay())).stream()
                .filter(booking -> booking.id() != excludeBookingId && booking.serviceBayId() != null)
                .map(booking -> new Occupancy(booking.serviceBayId(), toLocal(booking.scheduledStart()),
                        toLocal(booking.scheduledEnd())))
                .toList();
        return AvailabilityPolicy.slots(date, window, activeBays, taken, duration, settings.slotMinutes(),
                LocalDateTime.ofInstant(clock.instant(), settings.zone()));
    }

    /** La excepcion del dia manda sobre el horario semanal; un dia sin horario esta cerrado. */
    private Optional<DayWindow> windowOf(LocalDate date) {
        Optional<HoursException> exception = schedule.findExceptionByDate(date);
        if (exception.isPresent()) {
            return exception.get().window();
        }
        short day = (short) date.getDayOfWeek().getValue();
        return schedule.findBusinessHours().stream()
                .filter(hour -> hour.dayOfWeek() == day)
                .findFirst()
                .flatMap(BusinessHour::window);
    }

    private void requireBookableDate(LocalDate date) {
        if (date == null) {
            throw new InvalidValueException("DATE_REQUIRED", "Choose a date");
        }
        LocalDate today = today();
        if (date.isBefore(today) || date.isAfter(today.plusDays(settings.maxDaysAhead()))) {
            throw new InvalidValueException("INVALID_DATE",
                    "The date must be between today and " + settings.maxDaysAhead() + " days ahead");
        }
    }

    private VehicleSnapshot requireVehicle(long vehicleId, Caller caller) {
        Optional<VehicleSnapshot> vehicle = caller.admin()
                ? Optional.ofNullable(customers.vehiclesByIds(List.of(vehicleId)).get(vehicleId))
                : customers.ownVehicle(vehicleId);
        return vehicle.orElseThrow(() -> new NotFoundException("VEHICLE_NOT_FOUND",
                "Vehicle " + vehicleId + " does not exist"));
    }

    /** El vehiculo de la reserva visto por quien llama; null si customer ya no lo tiene. */
    private VehicleSnapshot vehicleOf(Booking booking, Caller caller) {
        if (caller.admin()) {
            return customers.vehiclesByIds(List.of(booking.customerVehicleId())).get(booking.customerVehicleId());
        }
        return customers.ownVehicle(booking.customerVehicleId()).orElse(null);
    }

    /**
     * Un cliente solo ve sus reservas (las que hizo o las de sus vehiculos); si no es suya es 404,
     * asi no se entera de que existe.
     */
    private Booking requireVisible(long bookingId, Caller caller) {
        Booking booking = requireBooking(bookingId);
        if (caller.admin() || booking.bookedBy() == caller.userId()) {
            return booking;
        }
        boolean ownsVehicle = customers.ownVehicles().stream().anyMatch(v -> v.id() == booking.customerVehicleId());
        if (!ownsVehicle) {
            throw new NotFoundException("BOOKING_NOT_FOUND", "Booking " + bookingId + " does not exist");
        }
        return booking;
    }

    private Booking requireBooking(long bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking " + bookingId + " does not exist"));
    }

    /** RF-007: el cliente no la cambia ni la cancela si ya empezo o si ya paso la hora. */
    private void requireChangeableBy(Booking booking, Caller caller) {
        boolean changeable = caller.admin() ? booking.status().isChangeable()
                : booking.changeableByCustomer(clock.instant());
        if (!changeable) {
            throw new ConflictException("BOOKING_NOT_CHANGEABLE", "This booking can no longer be changed");
        }
    }

    private CancellationReason requireReason(String code) {
        return bookings.findCancellationReason(code)
                .orElseThrow(() -> new InvalidValueException("INVALID_REASON", "Unknown cancellation reason " + code));
    }

    private static void requireAdmin(Caller caller) {
        if (!caller.admin()) {
            throw new NotFoundException("BOOKING_NOT_FOUND", "Not found");
        }
    }

    private static Long customerUserOf(VehicleSnapshot vehicle, Caller caller) {
        if (!caller.admin()) {
            return caller.userId();
        }
        return vehicle == null ? null : vehicle.ownerUserId();
    }

    private void publish(BookingEvent.Type type, Booking booking, Long customerUserId, String reason) {
        events.publish(new BookingEvent(type, booking.id(), booking.code(), customerUserId,
                booking.customerVehicleId(), booking.scheduledStart(), booking.serviceBayId(), reason, clock.instant()));
    }

    private BookingView view(Booking booking, VehicleSnapshot vehicle, Caller caller) {
        return view(booking, vehicle, baysById(), caller);
    }

    private BookingView view(Booking booking, VehicleSnapshot vehicle, Map<Short, ServiceBay> bayById, Caller caller) {
        boolean changeable = caller.admin() ? booking.status().isChangeable()
                : booking.changeableByCustomer(clock.instant());
        ServiceBay bay = booking.serviceBayId() == null ? null : bayById.get(booking.serviceBayId());
        return new BookingView(booking, vehicle, bay, toLocal(booking.scheduledStart()),
                toLocal(booking.scheduledEnd()), changeable);
    }

    /** Solo las bahias no borradas: una reserva vieja en una bahia ya borrada se ve sin bahia. */
    private Map<Short, ServiceBay> baysById() {
        return bays.findAll().stream().collect(Collectors.toMap(ServiceBay::id, Function.identity()));
    }

    private static int durationOf(List<BookingLine> lines) {
        return lines.stream().mapToInt(BookingLine::minutes).sum();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    private Instant toInstant(LocalDateTime local) {
        return local.atZone(settings.zone()).toInstant();
    }

    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(Objects.requireNonNull(instant), settings.zone());
    }
}
