package com.lavarapido.booking.domain.port.in;

import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.BusinessHour;
import com.lavarapido.booking.domain.model.Establishment;
import com.lavarapido.booking.domain.model.HoursException;
import com.lavarapido.booking.domain.model.ServiceBay;

import java.time.LocalDate;
import java.util.List;

/** Horario semanal, excepciones, bahias y datos de la sede. */
public interface ScheduleUseCase {

    /** Siempre los 7 dias, de lunes a domingo. */
    List<BusinessHour> businessHours();

    List<BusinessHour> updateBusinessHours(List<BusinessHour> hours, long actor);

    List<HoursException> exceptions(LocalDate from);

    HoursException createException(HoursException exception, long actor);

    HoursException updateException(int exceptionId, HoursException exception, long actor);

    void deleteException(int exceptionId, long actor);

    List<ServiceBay> bays();

    ServiceBay createBay(String name, BayStatus status, long actor);

    ServiceBay updateBay(short bayId, String name, BayStatus status, long actor);

    void deleteBay(short bayId, long actor);

    Establishment establishment();
}
