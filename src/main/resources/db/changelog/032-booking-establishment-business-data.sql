--liquibase formatted sql
-- Datos del negocio que el admin edita en Configuracion y que hasta ahora solo quedaban en el
-- navegador o en el celular: informacion legal, fiscal y canales oficiales. Todas las columnas
-- aceptan NULL: el NIT, la razon social y la direccion ya eran obligatorios desde la 005. Los valores
-- de ejemplo van en la semilla 110, que corre despues de crear la fila (109).

--changeset lavarapido:booking-032-establishment-business-data
--comment: Informacion legal, fiscal y canales oficiales del negocio
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('booking.establishment') AND name = 'business_type'
ALTER TABLE [booking].establishment ADD
    business_type                 NVARCHAR(100) NULL,
    founded_on                    DATE          NULL,
    legal_representative          NVARCHAR(150) NULL,
    legal_representative_document NVARCHAR(30)  NULL,
    website                       NVARCHAR(200) NULL,
    tax_regime                    NVARCHAR(150) NULL,
    ciiu_activity                 NVARCHAR(150) NULL,
    dian_resolution               NVARCHAR(60)  NULL,
    invoice_prefix                NVARCHAR(10)  NULL,
    invoice_range                 NVARCHAR(30)  NULL,
    instagram                     NVARCHAR(100) NULL,
    facebook                      NVARCHAR(150) NULL,
    support_line                  NVARCHAR(30)  NULL,
    service_hours_note            NVARCHAR(150) NULL;
--rollback ALTER TABLE [booking].establishment DROP COLUMN business_type, founded_on, legal_representative, legal_representative_document, website, tax_regime, ciiu_activity, dian_resolution, invoice_prefix, invoice_range, instagram, facebook, support_line, service_hours_note;
