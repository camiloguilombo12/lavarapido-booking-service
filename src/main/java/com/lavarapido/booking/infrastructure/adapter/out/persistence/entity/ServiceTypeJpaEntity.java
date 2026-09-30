package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

// entidad JPA para la tabla service_type
@Entity
@Table(name = "service_type", schema = "catalog")
public class ServiceTypeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_type_id")
    private Long serviceTypeId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "is_active")
    private Boolean isActive;

    // getters y setters
    public Long getServiceTypeId() { return serviceTypeId; }
    public void setServiceTypeId(Long serviceTypeId) { this.serviceTypeId = serviceTypeId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
