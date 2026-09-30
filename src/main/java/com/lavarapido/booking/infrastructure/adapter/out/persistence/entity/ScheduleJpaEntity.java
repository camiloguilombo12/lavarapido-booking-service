package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalTime;

// entidad JPA para la tabla schedule
@Entity
@Table(name = "schedule", schema = "booking")
public class ScheduleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes")
    private Integer slotDurationMinutes;

    @Column(name = "is_active")
    private Boolean isActive;

    // getters y setters
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public Integer getSlotDurationMinutes() { return slotDurationMinutes; }
    public void setSlotDurationMinutes(Integer slotDurationMinutes) { this.slotDurationMinutes = slotDurationMinutes; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
