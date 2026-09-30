package com.lavarapido.booking.domain.service;

import com.lavarapido.booking.domain.model.DayWindow;
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
}
