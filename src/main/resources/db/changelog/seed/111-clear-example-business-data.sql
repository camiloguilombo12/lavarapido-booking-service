--liquibase formatted sql
-- Los datos fiscales, los canales y los datos legales opcionales del seed 110 eran de ejemplo
-- (un "Express Car Wash" inventado). Se dejan vacíos: se completan desde Configuración cuando el
-- sistema se implemente para un negocio real. Solo se borran si siguen siendo los de ejemplo.

--changeset lavarapido:booking-111-clear-example-business-data
--comment: Vacía los datos de ejemplo del seed 110; se llenan con los del negocio real
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM [booking].establishment WHERE dian_resolution = N'No. 18764002345678' AND deleted_at IS NULL
UPDATE [booking].establishment SET
    business_type                 = NULL,
    founded_on                    = NULL,
    legal_representative          = NULL,
    legal_representative_document = NULL,
    website                       = NULL,
    tax_regime                    = NULL,
    ciiu_activity                 = NULL,
    dian_resolution               = NULL,
    invoice_prefix                = NULL,
    invoice_range                 = NULL,
    instagram                     = NULL,
    facebook                      = NULL,
    support_line                  = NULL,
    service_hours_note            = NULL
WHERE dian_resolution = N'No. 18764002345678' AND deleted_at IS NULL;
