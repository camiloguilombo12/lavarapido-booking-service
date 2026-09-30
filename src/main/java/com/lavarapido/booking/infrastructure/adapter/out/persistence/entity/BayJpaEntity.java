package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;

// entidad JPA para la tabla bay
@Entity
@Table(name = "bay", schema = "booking")
public class BayJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bay_id")
    private Long bayId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_active")
    private Boolean isActive;

    // getters y setters
    public Long getBayId() { return bayId; }
    public void setBayId(Long bayId) { this.bayId = bayId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
