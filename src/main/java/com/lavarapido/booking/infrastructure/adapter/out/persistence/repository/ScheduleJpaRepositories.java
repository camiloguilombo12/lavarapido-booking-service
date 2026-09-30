package com.lavarapido.booking.infrastructure.adapter.out.persistence.repository;

import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BusinessHourExceptionJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BusinessHourJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.EstablishmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Repositorios del horario, las excepciones y el negocio (esquema booking). */
public final class ScheduleJpaRepositories {

    private ScheduleJpaRepositories() {
    }

    public interface BusinessHours extends JpaRepository<BusinessHourJpaEntity, Short> {

        List<BusinessHourJpaEntity> findByDeletedAtIsNullOrderByDayOfWeekAsc();

        /** Incluye las borradas: uq_business_hour_day no es filtrado. */
        Optional<BusinessHourJpaEntity> findByDayOfWeek(short dayOfWeek);
    }

    public interface HourExceptions extends JpaRepository<BusinessHourExceptionJpaEntity, Integer> {

        List<BusinessHourExceptionJpaEntity> findByExceptionDateGreaterThanEqualAndDeletedAtIsNullOrderByExceptionDateAsc(
                LocalDate from);

        Optional<BusinessHourExceptionJpaEntity> findByIdAndDeletedAtIsNull(int id);

        Optional<BusinessHourExceptionJpaEntity> findByExceptionDateAndDeletedAtIsNull(LocalDate date);

        /** Incluye las borradas: uq_business_hour_exception_date no es filtrado. */
        Optional<BusinessHourExceptionJpaEntity> findByExceptionDate(LocalDate date);
    }

    public interface Establishments extends JpaRepository<EstablishmentJpaEntity, Short> {

        Optional<EstablishmentJpaEntity> findFirstByDeletedAtIsNull();
    }
}
