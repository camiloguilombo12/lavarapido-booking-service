package com.lavarapido.booking.application.usecase;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.InvalidValueException;
import com.lavarapido.booking.domain.exception.NotFoundException;
import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.Establishment;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.port.in.ScheduleUseCase;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.domain.port.out.BookingRepository;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
        schedule.saveBusinessHours(hours, actor);
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
        int id = schedule.saveException(exception, actor);
        return requireException(id);
    }

    @Override
    public HoursException updateException(int exceptionId, HoursException exception, long actor) {
        requireException(exceptionId);
        requireNotPast(exception.date());
        schedule.findExceptionByDate(exception.date())
                .filter(other -> other.id() != exceptionId)
                .ifPresent(other -> {
                    throw new ConflictException("EXCEPTION_DATE_TAKEN", "That date already has an exception");
                });
        HoursException changed = new HoursException(exceptionId, exception.date(), exception.closed(),
                exception.opensAt(), exception.closesAt(), exception.reason());
        schedule.saveException(changed, actor);
        return requireException(exceptionId);
    }

    @Override
    public void deleteException(int exceptionId, long actor) {
        requireException(exceptionId);
        schedule.deleteException(exceptionId, actor, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceBay> bays() {
        return bays.findAll();
    }

    @Override
    public ServiceBay createBay(String name, BayStatus status, long actor) {
        String cleanName = ServiceBay.requireName(name);
        if (bays.existsName(cleanName, null)) {
            throw new ConflictException("BAY_NAME_TAKEN", "Another bay already has that name");
        }
        return bays.insert(cleanName, status == null ? BayStatus.ACTIVE : status, actor);
    }

    @Override
    public ServiceBay updateBay(short bayId, String name, BayStatus status, long actor) {
        requireBay(bayId);
        String cleanName = ServiceBay.requireName(name);
        if (bays.existsName(cleanName, bayId)) {
            throw new ConflictException("BAY_NAME_TAKEN", "Another bay already has that name");
        }
        bays.update(bayId, cleanName, status, actor);
        return requireBay(bayId);
    }

    /** No se borra una bahia con reservas por delante: primero hay que moverlas o cancelarlas. */
    @Override
    public void deleteBay(short bayId, long actor) {
        requireBay(bayId);
        if (bookings.hasUpcomingOnBay(bayId, clock.instant())) {
            throw new ConflictException("BAY_HAS_BOOKINGS", "The bay has upcoming bookings");
        }
        bays.softDelete(bayId, actor, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public Establishment establishment() {
        return schedule.findEstablishment()
                .orElseThrow(() -> new NotFoundException("ESTABLISHMENT_NOT_FOUND", "The business data is not set"));
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

    private void requireNotPast(LocalDate date) {
        if (date.isBefore(today())) {
            throw new InvalidValueException("INVALID_DATE", "The date cannot be in the past");
        }
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }
}
