package com.lavarapido.booking.domain.service;

import com.lavarapido.booking.domain.model.DayWindow;
import com.lavarapido.booking.domain.model.UnavailableReason;
import com.lavarapido.booking.domain.service.AvailabilityPolicy.Occupancy;
import com.lavarapido.booking.domain.service.AvailabilityPolicy.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Disponibilidad (RF-006)")
class AvailabilityPolicyTest {

    private static final LocalDate DAY = LocalDate.parse("2026-10-01");
    private static final LocalDateTime YESTERDAY = DAY.minusDays(1).atStartOfDay();
    private static final Optional<DayWindow> NINE_TO_TWELVE =
            Optional.of(new DayWindow(LocalTime.of(9, 0), LocalTime.of(12, 0), null, null));

    private static Slot at(List<Slot> slots, String time) {
        return slots.stream().filter(slot -> slot.time().equals(LocalTime.parse(time))).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("el servicio tiene que terminar antes del cierre")
    void lastSlotEndsBeforeClosing() {
        List<Slot> slots = AvailabilityPolicy.slots(DAY, NINE_TO_TWELVE, List.of((short) 1), List.of(), 60, 30, YESTERDAY);

        assertEquals(LocalTime.of(9, 0), slots.getFirst().time());
        assertEquals(LocalTime.of(11, 0), slots.getLast().time());
        assertTrue(slots.stream().allMatch(Slot::available));
    }

    @Test
    @DisplayName("el motivo distingue día cerrado, fuera del horario, hora pasada y bahías llenas")
    void reasonOfAnUnavailableTime() {
        List<Occupancy> taken = List.of(new Occupancy((short) 1, DAY.atTime(9, 0), DAY.atTime(10, 0)));
        List<Slot> slots = AvailabilityPolicy.slots(DAY, NINE_TO_TWELVE, List.of((short) 1), taken, 60, 30, YESTERDAY);

        assertEquals(UnavailableReason.CLOSED_DAY,
                AvailabilityPolicy.whyUnavailable(List.of(), Optional.empty(), DAY.atTime(9, 0), YESTERDAY));
        assertEquals(UnavailableReason.OUTSIDE_HOURS,
                AvailabilityPolicy.whyUnavailable(slots, NINE_TO_TWELVE, DAY.atTime(8, 0), YESTERDAY));
        // cabe a las 11:00 pero no a las 11:30: terminaría después del cierre
        assertEquals(UnavailableReason.OUTSIDE_HOURS,
                AvailabilityPolicy.whyUnavailable(slots, NINE_TO_TWELVE, DAY.atTime(11, 30), YESTERDAY));
        assertEquals(UnavailableReason.NO_FREE_BAY,
                AvailabilityPolicy.whyUnavailable(slots, NINE_TO_TWELVE, DAY.atTime(9, 0), YESTERDAY));
        assertEquals(UnavailableReason.TIME_PASSED,
                AvailabilityPolicy.whyUnavailable(slots, NINE_TO_TWELVE, DAY.atTime(10, 0), DAY.atTime(10, 30)));
    }

    @Test
    @DisplayName("un dia cerrado no tiene horas")
    void closedDayHasNoSlots() {
        assertTrue(AvailabilityPolicy.slots(DAY, Optional.empty(), List.of((short) 1), List.of(), 45, 30, YESTERDAY).isEmpty());
    }

    @Test
    @DisplayName("sin bahia libre la hora no esta disponible, con otra bahia si")
    void occupiedBayIsSkipped() {
        List<Occupancy> taken = List.of(new Occupancy((short) 1, DAY.atTime(9, 0), DAY.atTime(10, 0)));

        List<Slot> oneBay = AvailabilityPolicy.slots(DAY, NINE_TO_TWELVE, List.of((short) 1), taken, 60, 30, YESTERDAY);
        assertFalse(at(oneBay, "09:00").available());
        assertFalse(at(oneBay, "09:30").available());
        assertTrue(at(oneBay, "10:00").available());

        List<Slot> twoBays = AvailabilityPolicy.slots(DAY, NINE_TO_TWELVE, List.of((short) 1, (short) 2), taken, 60, 30,
                YESTERDAY);
        assertEquals((short) 2, at(twoBays, "09:00").bayId());
    }

    @Test
    @DisplayName("no ofrece horas que se crucen con la pausa")
    void breakIsRespected() {
        Optional<DayWindow> withLunch = Optional.of(new DayWindow(LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0)));

        List<Slot> slots = AvailabilityPolicy.slots(DAY, withLunch, List.of((short) 1), List.of(), 60, 30, YESTERDAY);

        assertTrue(slots.stream().noneMatch(slot -> slot.time().equals(LocalTime.of(11, 30))));
        assertTrue(slots.stream().noneMatch(slot -> slot.time().equals(LocalTime.of(12, 0))));
        assertTrue(slots.stream().anyMatch(slot -> slot.time().equals(LocalTime.of(11, 0))));
        assertTrue(slots.stream().anyMatch(slot -> slot.time().equals(LocalTime.of(13, 0))));
    }

    @Test
    @DisplayName("las horas que ya pasaron no se pueden reservar")
    void pastTimesAreUnavailable() {
        List<Slot> slots = AvailabilityPolicy.slots(DAY, NINE_TO_TWELVE, List.of((short) 1), List.of(), 30, 30,
                DAY.atTime(10, 0));

        assertFalse(at(slots, "09:30").available());
        assertFalse(at(slots, "10:00").available());
        assertTrue(at(slots, "10:30").available());
    }

    @Test
    @DisplayName("las alternativas son las libres mas cercanas, maximo N y en orden")
    void alternativesAreTheClosestFreeTimes() {
        List<Occupancy> taken = List.of(new Occupancy((short) 1, DAY.atTime(10, 0), DAY.atTime(11, 0)));
        List<Slot> slots = AvailabilityPolicy.slots(DAY, NINE_TO_TWELVE, List.of((short) 1), taken, 30, 30, YESTERDAY);

        List<LocalTime> alternatives = AvailabilityPolicy.alternatives(slots, LocalTime.of(10, 0), 3);

        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(11, 0)), alternatives);
    }

    @Test
    @DisplayName("las alternativas no ofrecen horas en que el vehículo ya tiene otra reserva")
    void alternativesSkipTimesWhenTheVehicleIsBusy() {
        // dos bahías libres toda la mañana, pero el carro ya tiene una reserva de 10:00 a 11:00
        List<Slot> slots = AvailabilityPolicy.slots(DAY, NINE_TO_TWELVE, List.of((short) 1, (short) 2), List.of(),
                60, 30, YESTERDAY);
        List<Occupancy> vehicleBusy = List.of(new Occupancy((short) 2, DAY.atTime(10, 0), DAY.atTime(11, 0)));

        List<Slot> forVehicle = AvailabilityPolicy.withoutBusyTimes(slots, DAY, 60, vehicleBusy);

        // 9:30, 10:00 y 10:30 se cruzan con 10:00-11:00; 9:00 termina justo cuando empieza y 11:00 empieza al final
        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                AvailabilityPolicy.alternatives(forVehicle, LocalTime.of(10, 0), 5));
        assertTrue(at(forVehicle, "09:00").available());
        assertFalse(at(forVehicle, "10:30").available());
        // sin reservas del vehículo la lista queda igual
        assertEquals(slots, AvailabilityPolicy.withoutBusyTimes(slots, DAY, 60, List.of()));
    }
}
