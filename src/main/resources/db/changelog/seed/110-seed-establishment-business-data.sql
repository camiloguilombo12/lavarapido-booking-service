--liquibase formatted sql
-- Datos legales, fiscales y canales del negocio (columnas de la migracion 032), con los mismos
-- valores de ejemplo que la web y la app tenian escritos a mano. Solo si la sede aun no los tiene.

--changeset lavarapido:booking-110-seed-establishment-business-data
--comment: Los mismos valores de ejemplo que la web y la app tenian escritos a mano
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM [booking].establishment WHERE business_type IS NULL
UPDATE [booking].establishment SET
    business_type                 = N'Lavado y Detailing Automotriz',
    founded_on                    = '2019-03-15',
    legal_representative          = N'Laura Méndez Ríos',
    legal_representative_document = N'CC 1.032.456.789',
    website                       = N'www.expresscarwash.co',
    tax_regime                    = N'Responsable de IVA 19% · Régimen Ordinario Común',
    ciiu_activity                 = N'4520 - Mantenimiento y reparación de vehículos',
    dian_resolution               = N'No. 18764002345678',
    invoice_prefix                = N'LV',
    invoice_range                 = N'4500-5500',
    instagram                     = N'@expresscarwash_oficial',
    facebook                      = N'Express Car Wash S.A.S.',
    support_line                  = N'018000-123-456',
    service_hours_note            = N'Lunes a sábado 7:00 AM - 6:30 PM'
WHERE deleted_at IS NULL;
