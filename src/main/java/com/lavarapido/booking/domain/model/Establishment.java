package com.lavarapido.booking.domain.model;

import com.lavarapido.booking.domain.exception.InvalidValueException;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Los datos del negocio (una sola fila): la sede donde el cliente lleva su vehiculo, su informacion
 * legal y fiscal y sus canales oficiales. El admin los edita en Configuracion (migracion 032).
 */
public record Establishment(String legalName, String tradeName, String taxId, String businessType,
                            LocalDate foundedOn, String legalRepresentative, String legalRepresentativeDocument,
                            String address, String phone, String email, String website, String taxRegime,
                            String ciiuActivity, String dianResolution, String invoicePrefix, String invoiceRange,
                            String instagram, String facebook, String supportLine, String serviceHoursNote,
                            String logoUrl) {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MIN_PHONE_DIGITS = 7;

    /**
     * Reglas para guardar: razon social, NIT y direccion obligatorios; telefono con al menos 7
     * digitos; correo valido; fecha de constitucion que no este en el futuro.
     */
    public Establishment requireValid(LocalDate today) {
        if (isBlank(legalName) || isBlank(taxId) || isBlank(address)) {
            throw new InvalidValueException("ESTABLISHMENT_REQUIRED_FIELD",
                    "Legal name, tax id and address are required");
        }
        if (!isBlank(phone) && phone.chars().filter(Character::isDigit).count() < MIN_PHONE_DIGITS) {
            throw new InvalidValueException("ESTABLISHMENT_INVALID_PHONE", "The phone needs at least 7 digits");
        }
        if (!isBlank(email) && !EMAIL.matcher(email.trim()).matches()) {
            throw new InvalidValueException("ESTABLISHMENT_INVALID_EMAIL", "The email is not valid");
        }
        if (foundedOn != null && foundedOn.isAfter(today)) {
            throw new InvalidValueException("ESTABLISHMENT_INVALID_FOUNDATION_DATE",
                    "The foundation date cannot be in the future");
        }
        return this;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
