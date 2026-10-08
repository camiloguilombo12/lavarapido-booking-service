package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Un movimiento del historial de "Horarios y bahias" (migracion 021). Solo se inserta, nunca se edita. */
@Entity
@Table(schema = "booking", name = "schedule_history")
public class ScheduleHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_history_id")
    private Long id;

    @Column(name = "entity_type", length = 20)
    private String entityType;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "detail", length = 300)
    private String detail;

    @Column(name = "changed_at", insertable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by")
    private Long changedBy;

    public ScheduleHistoryJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }
}
