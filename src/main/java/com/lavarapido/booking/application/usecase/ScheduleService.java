package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.exception.NotFoundException;
import com.lavarapido.booking.domain.exception.ScheduleConflictException;
import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.DayWindow;
import com.lavarapido.booking.domain.model.Establishment;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.model.ScheduleEntityType;
import com.lavarapido.booking.domain.model.ScheduleHistoryEntry;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.port.in.ScheduleUseCase;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import com.lavarapido.booking.domain.service.AvailabilityPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Horario semanal, excepciones, bahias y datos de la sede ("Horarios y bahias" del admin). */
@Service
@Transactional
public class ScheduleService implements ScheduleUseCase {

    private static final LocalTime DEFAULT_OPENS = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_CLOSES = LocalTime.of(17, 0);

    private final ScheduleRepository schedule;
    private final BayRepository bays;
    private final BookingRepository bookings;
    private final BookingSettings settings;
    private final Clock clock;

    public ScheduleService(ScheduleRepository schedule, BayRepository bays, BookingRepository bookings,
                           BookingSettings settings, Clock clock) {
        this.schedule = schedule;
        this.bays = bays;
        this.bookings = bookings;
        this.settings = settings;
        this.clock = clock;
    }

    /** Un dia sin fila en la base se muestra como cerrado, asi la pantalla siempre tiene los 7. */
    @Override
    @Transactional(readOnly = true)
    public List<BusinessHour> businessHours() {
        Map<Short, BusinessHour> saved = schedule.findBusinessHours().stream()
                .collect(Collectors.toMap(BusinessHour::dayOfWeek, Function.identity()));
        List<BusinessHour> week = new ArrayList<>();
        for (short day = 1; day <= 7; day++) {
            week.add(saved.getOrDefault(day, new BusinessHour(day, false, DEFAULT_OPENS, DEFAULT_CLOSES, null, null)));
        }
        return week;
    }

    @Override
    public List<BusinessHour> updateBusinessHours(List<BusinessHour> hours, long actor) {
        if (hours == null || hours.size() != 7
                || hours.stream().map(BusinessHour::dayOfWeek).distinct().count() != 7) {
            throw new InvalidValueException("INVALID_WEEK", "Send the 7 days of the week, one time each");
        }
        // BIZ-10: las reservas que vienen tienen que seguir cabiendo; un dia con excepcion no cambia
        bookings.lockSchedule();
        Map<Short, BusinessHour> week = hours.stream()
                .collect(Collectors.toMap(BusinessHour::dayOfWeek, Function.identity()));
        Set<LocalDate> withException = schedule.findExceptionsFrom(today()).stream()
                .map(HoursException::date)
                .collect(Collectors.toSet());
        requireBookingsFit(date -> !withException.contains(date),
                date -> Optional.ofNullable(week.get(dayOf(date))).flatMap(BusinessHour::window));
        schedule.saveBusinessHours(hours, actor);
        schedule.recordHistory(ScheduleEntityType.WEEK, "Horario semanal actualizado", null, actor);
        return businessHours();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HoursException> exceptions(LocalDate from) {
        return schedule.findExceptionsFrom(from == null ? today() : from);
    }

    @Override
    public HoursException createException(HoursException exception, long actor) {
        requireNotPast(exception.date());
        if (schedule.findExceptionByDate(exception.date()).isPresent()) {
            throw new ConflictException("EXCEPTION_DATE_TAKEN", "That date already has an exception");
        }
        // BIZ-10: un festivo o una jornada mas corta no puede dejar por fuera reservas de ese dia
        bookings.lockSchedule();
        requireBookingsFit(exception.date()::equals, date -> exception.window());
        int id = schedule.saveException(exception, actor);
        schedule.recordHistory(ScheduleEntityType.EXCEPTION, "Excepción agregada", exception.reason(), actor);
        return requireException(id);
    }

    @Override
    public HoursException updateException(int exceptionId, HoursException exception, long actor) {
        HoursException before = requireException(exceptionId);
        requireNotPast(exception.date());
        schedule.findExceptionByDate(exception.date())
                .filter(other -> other.id() != exceptionId)
                .ifPresent(other -> {
                    throw new ConflictException("EXCEPTION_DATE_TAKEN", "That date already has an exception");
                });
        // BIZ-10: el dia nuevo queda con este horario; si la fecha cambio, el dia viejo vuelve al semanal
        bookings.lockSchedule();
        requireBookingsFit(date -> date.equals(exception.date()) || date.equals(before.date()),
                date -> date.equals(exception.date()) ? exception.window() : weeklyWindow(date));
        HoursException changed = new HoursException(exceptionId, exception.date(), exception.closed(),
                exception.opensAt(), exception.closesAt(), exception.reason());
        schedule.saveException(changed, actor);
        schedule.recordHistory(ScheduleEntityType.EXCEPTION, "Excepción actualizada", exception.reason(), actor);
        return requireException(exceptionId);
    }

    @Override
    public void deleteException(int exceptionId, long actor) {
        HoursException target = requireException(exceptionId);
        // BIZ-10: sin la excepcion el dia vuelve al horario semanal (una jornada extendida se pierde)
        bookings.lockSchedule();
        requireBookingsFit(target.date()::equals, this::weeklyWindow);
        schedule.deleteException(exceptionId, actor, clock.instant());
        schedule.recordHistory(ScheduleEntityType.EXCEPTION, "Excepción eliminada", target.reason(), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceBay> bays() {
        return bays.findAll();
    }

    @Override
    public ServiceBay createBay(String name, BayStatus status, long actor) {
        String cleanName = ServiceBay.requireName(name);
        BayStatus initialStatus = ServiceBay.requireInitialStatus(status);
        if (bays.existsName(cleanName, null)) {
            throw new ConflictException("BAY_NAME_TAKEN", "Another bay already has that name");
        }
        ServiceBay created = bays.insert(cleanName, initialStatus, actor);
        schedule.recordHistory(ScheduleEntityType.BAY, "Bahía agregada", created.name(), actor);
        return created;
    }

    @Override
    public ServiceBay updateBay(short bayId, String name, BayStatus status, long actor) {
        ServiceBay before = requireBay(bayId);
        String cleanName = ServiceBay.requireName(name);
        if (bays.existsName(cleanName, bayId)) {
            throw new ConflictException("BAY_NAME_TAKEN", "Another bay already has that name");
        }
        // BIZ-10: una bahia que deja de recibir reservas (mantenimiento, inactiva) no puede tener reservas por delante
        if (before.status().acceptsBookings() && !status.acceptsBookings()) {
            bookings.lockSchedule();
            List<String> onBay = bookings.findActiveEndingAfter(clock.instant()).stream()
                    .filter(booking -> booking.serviceBayId() != null && booking.serviceBayId() == bayId)
                    .map(Booking::code)
                    .toList();
            if (!onBay.isEmpty()) {
                throw new ScheduleConflictException(onBay);
            }
        }
        bays.update(bayId, cleanName, status, actor);
        ServiceBay after = requireBay(bayId);
        schedule.recordHistory(ScheduleEntityType.BAY,
                before.status() != after.status() ? "Bahía cambió de estado" : "Bahía actualizada",
                after.name() + " · " + after.status().name(), actor);
        return after;
    }

    /** No se borra una bahia con reservas por delante: primero hay que moverlas o cancelarlas. */
    @Override
    public void deleteBay(short bayId, long actor) {
        ServiceBay target = requireBay(bayId);
        if (bookings.hasUpcomingOnBay(bayId, clock.instant())) {
            throw new ConflictException("BAY_HAS_BOOKINGS", "The bay has upcoming bookings");
        }
        bays.softDelete(bayId, actor, clock.instant());
        schedule.recordHistory(ScheduleEntityType.BAY, "Bahía eliminada", target.name(), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Establishment establishment() {
        return schedule.findEstablishment()
                .orElseThrow(() -> new NotFoundException("ESTABLISHMENT_NOT_FOUND", "The business data is not set"));
    }

    @Override
    public Establishment updateEstablishment(Establishment changes, long actor) {
        Establishment current = establishment();
        String tradeName = changes.tradeName() == null || changes.tradeName().isBlank()
                ? current.tradeName() : changes.tradeName();
        Establishment next = new Establishment(trim(changes.legalName()), tradeName, trim(changes.taxId()),
                trim(changes.businessType()), changes.foundedOn(), trim(changes.legalRepresentative()),
                trim(changes.legalRepresentativeDocument()), trim(changes.address()), trim(changes.phone()),
                trim(changes.email()), trim(changes.website()), trim(changes.taxRegime()), trim(changes.ciiuActivity()),
                trim(changes.dianResolution()), trim(changes.invoicePrefix()), trim(changes.invoiceRange()),
                trim(changes.instagram()), trim(changes.facebook()), trim(changes.supportLine()),
                trim(changes.serviceHoursNote()), current.logoUrl())
                .requireValid(LocalDate.now(clock.withZone(settings.zone())));
        schedule.saveEstablishment(next, actor);
        return next;
    }

    /** Vacio se guarda como NULL, asi la base no queda con textos en blanco. */
    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleHistoryEntry> history() {
        return schedule.findHistory(100);
    }

    private ServiceBay requireBay(short bayId) {
        return bays.findById(bayId)
                .orElseThrow(() -> new NotFoundException("BAY_NOT_FOUND", "Bay " + bayId + " does not exist"));
    }

    private HoursException requireException(int exceptionId) {
        return schedule.findException(exceptionId)
                .orElseThrow(() -> new NotFoundException("EXCEPTION_NOT_FOUND",
                        "Exception " + exceptionId + " does not exist"));
    }

    /**
     * BIZ-10: las reservas activas de los dias afectados tienen que caber en el horario nuevo de ese
     * dia; si alguna queda por fuera, 409 SCHEDULE_CONFLICTS_BOOKINGS con cuantas son. Se llama con la
     * agenda bloqueada, asi nadie reserva con el horario viejo mientras tanto.
     */
    private void requireBookingsFit(Predicate<LocalDate> affects, Function<LocalDate, Optional<DayWindow>> newWindow) {
        List<String> outside = bookings.findActiveEndingAfter(clock.instant()).stream()
                .filter(booking -> {
                    LocalDateTime start = toLocal(booking.scheduledStart());
                    LocalDate date = start.toLocalDate();
                    return affects.test(date)
                            && !AvailabilityPolicy.fits(date, newWindow.apply(date), start, toLocal(booking.scheduledEnd()));
                })
                .map(Booking::code)
                .toList();
        if (!outside.isEmpty()) {
            throw new ScheduleConflictException(outside);
        }
    }

    /** El horario semanal guardado para ese dia (sin la excepcion). */
    private Optional<DayWindow> weeklyWindow(LocalDate date) {
        return schedule.findBusinessHours().stream()
                .filter(hour -> hour.dayOfWeek() == dayOf(date))
                .findFirst()
                .flatMap(BusinessHour::window);
    }

    private static short dayOf(LocalDate date) {
        return (short) date.getDayOfWeek().getValue();
    }

    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, settings.zone());
    }

    private void requireNotPast(LocalDate date) {
        if (date.isBefore(today())) {
            throw new InvalidValueException("INVALID_DATE", "The date cannot be in the past");
        }
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }
}
