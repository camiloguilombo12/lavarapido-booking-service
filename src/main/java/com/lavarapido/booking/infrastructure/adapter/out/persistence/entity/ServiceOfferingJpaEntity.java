package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

// entidad JPA para la tabla service_offering
@Entity
@Table(name = "service_offering", schema = "catalog")
public class ServiceOfferingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_offering_id")
    private Long serviceOfferingId;

    @Column(name = "service_type_id", nullable = false)
    private Long serviceTypeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "is_active")
    private Boolean isActive;

    // getters y setters
    public Long getServiceOfferingId() { return serviceOfferingId; }
    public void setServiceOfferingId(Long serviceOfferingId) { this.serviceOfferingId = serviceOfferingId; }
    public Long getServiceTypeId() { return serviceTypeId; }
    public void setServiceTypeId(Long serviceTypeId) { this.serviceTypeId = serviceTypeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
