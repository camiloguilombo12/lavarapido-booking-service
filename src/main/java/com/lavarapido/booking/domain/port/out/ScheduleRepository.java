package com.lavarapido.booking.domain.port.out;

import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.Establishment;
import com.lavarapido.booking.domain.model.HoursException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Horario semanal, excepciones y datos del negocio (esquema booking). */
public interface ScheduleRepository {

    /** Los dias configurados, de lunes (1) a domingo (7). Un dia sin fila es un dia cerrado. */
    List<BusinessHour> findBusinessHours();

    void saveBusinessHours(List<BusinessHour> hours, long actor);

    List<HoursException> findExceptionsFrom(LocalDate from);

    Optional<HoursException> findException(int exceptionId);

    Optional<HoursException> findExceptionByDate(LocalDate date);

    /** Crea o actualiza (id 0 = nueva) y devuelve el id. */
    int saveException(HoursException exception, long actor);

    void deleteException(int exceptionId, long actor, Instant now);

    Optional<Establishment> findEstablishment();
}
