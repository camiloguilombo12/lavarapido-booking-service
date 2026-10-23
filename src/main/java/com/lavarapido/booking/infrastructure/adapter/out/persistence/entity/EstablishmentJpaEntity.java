package com.lavarapido.booking.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * La unica fila de booking.establishment: los datos del negocio.
 *
 * updated_at y row_version los pone el trigger de la tabla, nunca esta clase.
 */
@Entity
@Table(schema = "booking", name = "establishment")
public class EstablishmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "establishment_id")
    private Short id;

    @Column(name = "legal_name", length = 150)
    private String legalName;

    @Column(name = "trade_name", length = 150)
    private String tradeName;

    @Column(name = "tax_id", length = 30)
    private String taxId;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    // datos legales, fiscales y canales (migracion 032)
    @Column(name = "business_type", length = 100)
    private String businessType;

    @Column(name = "founded_on")
    private LocalDate foundedOn;

    @Column(name = "legal_representative", length = 150)
    private String legalRepresentative;

    @Column(name = "legal_representative_document", length = 30)
    private String legalRepresentativeDocument;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "tax_regime", length = 150)
    private String taxRegime;

    @Column(name = "ciiu_activity", length = 150)
    private String ciiuActivity;

    @Column(name = "dian_resolution", length = 60)
    private String dianResolution;

    @Column(name = "invoice_prefix", length = 10)
    private String invoicePrefix;

    @Column(name = "invoice_range", length = 30)
    private String invoiceRange;

    @Column(name = "instagram", length = 100)
    private String instagram;

    @Column(name = "facebook", length = 150)
    private String facebook;

    @Column(name = "support_line", length = 30)
    private String supportLine;

    @Column(name = "service_hours_note", length = 150)
    private String serviceHoursNote;

    @Column(name = "singleton", insertable = false, updatable = false)
    private Boolean singleton;

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

    public EstablishmentJpaEntity() {
    }

    public Short getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public LocalDate getFoundedOn() {
        return foundedOn;
    }

    public void setFoundedOn(LocalDate foundedOn) {
        this.foundedOn = foundedOn;
    }

    public String getLegalRepresentative() {
        return legalRepresentative;
    }

    public void setLegalRepresentative(String legalRepresentative) {
        this.legalRepresentative = legalRepresentative;
    }

    public String getLegalRepresentativeDocument() {
        return legalRepresentativeDocument;
    }

    public void setLegalRepresentativeDocument(String legalRepresentativeDocument) {
        this.legalRepresentativeDocument = legalRepresentativeDocument;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public String getCiiuActivity() {
        return ciiuActivity;
    }

    public void setCiiuActivity(String ciiuActivity) {
        this.ciiuActivity = ciiuActivity;
    }

    public String getDianResolution() {
        return dianResolution;
    }

    public void setDianResolution(String dianResolution) {
        this.dianResolution = dianResolution;
    }

    public String getInvoicePrefix() {
        return invoicePrefix;
    }

    public void setInvoicePrefix(String invoicePrefix) {
        this.invoicePrefix = invoicePrefix;
    }

    public String getInvoiceRange() {
        return invoiceRange;
    }

    public void setInvoiceRange(String invoiceRange) {
        this.invoiceRange = invoiceRange;
    }

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }

    public String getFacebook() {
        return facebook;
    }

    public void setFacebook(String facebook) {
        this.facebook = facebook;
    }

    public String getSupportLine() {
        return supportLine;
    }

    public void setSupportLine(String supportLine) {
        this.supportLine = supportLine;
    }

    public String getServiceHoursNote() {
        return serviceHoursNote;
    }

    public void setServiceHoursNote(String serviceHoursNote) {
        this.serviceHoursNote = serviceHoursNote;
    }

    public Boolean getSingleton() {
        return singleton;
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
