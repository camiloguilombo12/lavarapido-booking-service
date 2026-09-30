package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.Establishment;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.port.out.ScheduleRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BusinessHourExceptionJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BusinessHourJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ScheduleJpaRepositories;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Horario semanal, excepciones y datos del negocio (esquema booking). */
@Repository
class SchedulePersistenceAdapter implements ScheduleRepository {

    private final ScheduleJpaRepositories.BusinessHours hours;
    private final ScheduleJpaRepositories.HourExceptions exceptions;
    private final ScheduleJpaRepositories.Establishments establishments;

    SchedulePersistenceAdapter(ScheduleJpaRepositories.BusinessHours hours,
                               ScheduleJpaRepositories.HourExceptions exceptions,
                               ScheduleJpaRepositories.Establishments establishments) {
        this.hours = hours;
        this.exceptions = exceptions;
        this.establishments = establishments;
    }

    @Override
    public List<BusinessHour> findBusinessHours() {
        return hours.findByDeletedAtIsNullOrderByDayOfWeekAsc().stream()
                .map(entity -> new BusinessHour(entity.getDayOfWeek(), Boolean.TRUE.equals(entity.getActive()),
                        entity.getOpensAt(), entity.getClosesAt(), entity.getBreakStartsAt(), entity.getBreakEndsAt()))
                .toList();
    }

    /** Un dia sin fila (o borrada) se crea o se revive: uq_business_hour_day no es filtrado. */
    @Override
    public void saveBusinessHours(List<BusinessHour> week, long actor) {
        for (BusinessHour day : week) {
            BusinessHourJpaEntity entity = hours.findByDayOfWeek(day.dayOfWeek()).orElseGet(() -> {
                BusinessHourJpaEntity created = new BusinessHourJpaEntity();
                created.setDayOfWeek(day.dayOfWeek());
                created.setCreatedBy(actor);
                return created;
            });
            entity.setOpensAt(day.opensAt());
            entity.setClosesAt(day.closesAt());
            entity.setBreakStartsAt(day.breakStartsAt());
            entity.setBreakEndsAt(day.breakEndsAt());
            entity.setActive(day.working());
            entity.setDeletedAt(null);
            entity.setDeletedBy(null);
            entity.setUpdatedBy(actor);
            hours.save(entity);
        }
    }

    @Override
    public List<HoursException> findExceptionsFrom(LocalDate from) {
        return exceptions.findByExceptionDateGreaterThanEqualAndDeletedAtIsNullOrderByExceptionDateAsc(from).stream()
                .map(SchedulePersistenceAdapter::toException)
                .toList();
    }

    @Override
    public Optional<HoursException> findException(int exceptionId) {
        return exceptions.findByIdAndDeletedAtIsNull(exceptionId).map(SchedulePersistenceAdapter::toException);
    }

    @Override
    public Optional<HoursException> findExceptionByDate(LocalDate date) {
        return exceptions.findByExceptionDateAndDeletedAtIsNull(date).map(SchedulePersistenceAdapter::toException);
    }

    /**
     * id 0 = nueva. Si esa fecha tuvo una excepcion que se borro, se reutiliza la fila, porque
     * la fecha es unica aunque la fila este borrada.
     */
    @Override
    public int saveException(HoursException exception, long actor) {
        BusinessHourExceptionJpaEntity entity;
        if (exception.id() == 0) {
            entity = exceptions.findByExceptionDate(exception.date()).orElseGet(() -> {
                BusinessHourExceptionJpaEntity created = new BusinessHourExceptionJpaEntity();
                created.setCreatedBy(actor);
                return created;
            });
        } else {
            entity = exceptions.findById(exception.id()).orElseThrow();
            // si le cambian la fecha a una que tiene una fila borrada, esa fila estorba el unico
            exceptions.findByExceptionDate(exception.date())
                    .filter(other -> !other.getId().equals(exception.id()) && other.getDeletedAt() != null)
                    .ifPresent(stale -> {
                        exceptions.delete(stale);
                        exceptions.flush();
                    });
        }
        entity.setExceptionDate(exception.date());
        entity.setClosed(exception.closed());
        entity.setOpensAt(exception.opensAt());
        entity.setClosesAt(exception.closesAt());
        entity.setReason(exception.reason());
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
        entity.setUpdatedBy(actor);
        return exceptions.save(entity).getId();
    }

    @Override
    public void deleteException(int exceptionId, long actor, Instant now) {
        BusinessHourExceptionJpaEntity entity = exceptions.findById(exceptionId).orElseThrow();
        entity.setDeletedAt(now);
        entity.setDeletedBy(actor);
        exceptions.save(entity);
    }

    @Override
    public Optional<Establishment> findEstablishment() {
        return establishments.findFirstByDeletedAtIsNull()
                .map(entity -> new Establishment(entity.getLegalName(), entity.getTradeName(), entity.getTaxId(),
                        entity.getAddress(), entity.getPhone(), entity.getEmail(), entity.getLogoUrl()));
    }

    private static HoursException toException(BusinessHourExceptionJpaEntity entity) {
        return new HoursException(entity.getId(), entity.getExceptionDate(), Boolean.TRUE.equals(entity.getClosed()),
                entity.getOpensAt(), entity.getClosesAt(), entity.getReason());
    }
}
