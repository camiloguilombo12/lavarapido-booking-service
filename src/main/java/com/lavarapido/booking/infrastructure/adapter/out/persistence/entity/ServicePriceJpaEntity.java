package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * La fila de catalog.service_price. El trigger tr_service_price_immutable no deja cambiar precio,
 * duracion ni tipo: solo se puede cerrar (valid_to) o borrar logicamente.
 *
 * updated_at y row_version los pone el trigger de la tabla, nunca esta clase.
 */
@Entity
@Table(schema = "catalog", name = "service_price")
public class ServicePriceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_price_id")
    private Long id;

    @Column(name = "service_id", updatable = false)
    private Integer serviceId;

    @Column(name = "vehicle_type_id", updatable = false)
    private Short vehicleTypeId;

    @Column(name = "price", updatable = false)
    private BigDecimal price;

    @Column(name = "estimated_minutes", updatable = false)
    private Short estimatedMinutes;

    @Column(name = "valid_from", updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

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

    public ServicePriceJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public Short getVehicleTypeId() {
        return vehicleTypeId;
    }

    public void setVehicleTypeId(Short vehicleTypeId) {
        this.vehicleTypeId = vehicleTypeId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Short getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Short estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
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
