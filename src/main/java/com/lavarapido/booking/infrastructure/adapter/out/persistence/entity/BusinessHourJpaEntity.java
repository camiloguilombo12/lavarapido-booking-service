package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Un dia de booking.business_hour (1 = lunes ... 7 = domingo). La pausa la agrego la migracion 018.
 *
 * updated_at y row_version los pone el trigger de la tabla, nunca esta clase.
 */
@Entity
@Table(schema = "booking", name = "business_hour")
public class BusinessHourJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_hour_id")
    private Short id;

    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "break_starts_at")
    private LocalTime breakStartsAt;

    @Column(name = "break_ends_at")
    private LocalTime breakEndsAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;

    public BusinessHourJpaEntity() {
    }

    public Short getId() {
        return id;
    }

    public Short getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Short dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public void setOpensAt(LocalTime opensAt) {
        this.opensAt = opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(LocalTime closesAt) {
        this.closesAt = closesAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalTime getBreakStartsAt() {
        return breakStartsAt;
    }

    public void setBreakStartsAt(LocalTime breakStartsAt) {
        this.breakStartsAt = breakStartsAt;
    }

    public LocalTime getBreakEndsAt() {
        return breakEndsAt;
    }

    public void setBreakEndsAt(LocalTime breakEndsAt) {
        this.breakEndsAt = breakEndsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(Long deletedBy) {
        this.deletedBy = deletedBy;
    }

    public Integer getRowVersion() {
        return rowVersion;
    }
}
