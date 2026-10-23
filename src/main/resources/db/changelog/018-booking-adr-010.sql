--liquibase formatted sql
-- Cambios de ADR-010 que tocan al booking-service:
--   * una bahia puede estar ACTIVE, MAINTENANCE o INACTIVE (antes solo is_active);
--   * cada dia del horario puede tener una pausa (almuerzo).
-- La extension de establishment (datos de facturacion y ayuda) sigue pendiente: ADR-010 no
-- define los campos, asi que no se inventan aqui.

--changeset lavarapido:booking-018-bay-status
--comment: Catalogo de estados de bahia (ADR-010). Ids fijos: 1 ACTIVE, 2 MAINTENANCE, 3 INACTIVE
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'bay_status'
CREATE TABLE [booking].bay_status (
    bay_status_id SMALLINT     IDENTITY(1,1) NOT NULL,
    code          NVARCHAR(30) NOT NULL,
    name          NVARCHAR(60) NOT NULL,
    accepts_bookings BIT       NOT NULL CONSTRAINT df_baystatus_accepts DEFAULT 0,
    display_order SMALLINT     NOT NULL CONSTRAINT df_baystatus_order DEFAULT 0,
    created_at    DATETIME2(3) NOT NULL CONSTRAINT df_baystatus_created DEFAULT SYSUTCDATETIME(),
    created_by    BIGINT       NULL,
    updated_at    DATETIME2(3) NULL,
    updated_by    BIGINT       NULL,
    deleted_at    DATETIME2(3) NULL,
    deleted_by    BIGINT       NULL,
    row_version   INT          NOT NULL CONSTRAINT df_baystatus_rv DEFAULT 1,
    CONSTRAINT pk_bay_status PRIMARY KEY (bay_status_id),
    CONSTRAINT uq_bay_status_code UNIQUE (code)
);
SET IDENTITY_INSERT [booking].bay_status ON;
INSERT INTO [booking].bay_status (bay_status_id, code, name, accepts_bookings, display_order) VALUES
    (1, N'ACTIVE',      N'Activa',            1, 1),
    (2, N'MAINTENANCE', N'En mantenimiento',  0, 2),
    (3, N'INACTIVE',    N'Inactiva',          0, 3);
SET IDENTITY_INSERT [booking].bay_status OFF;

--changeset lavarapido:booking-018-service-bay-status
--comment: service_bay.is_active pasa a bay_status_id. Las bahias inactivas quedan en INACTIVE
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('booking.service_bay') AND name = 'bay_status_id'
ALTER TABLE [booking].service_bay ADD bay_status_id SMALLINT NOT NULL CONSTRAINT df_bay_status DEFAULT 1;
ALTER TABLE [booking].service_bay ADD CONSTRAINT fk_service_bay_status FOREIGN KEY (bay_status_id) REFERENCES [booking].bay_status(bay_status_id);
EXEC('UPDATE [booking].service_bay SET bay_status_id = 3 WHERE is_active = 0');
ALTER TABLE [booking].service_bay DROP CONSTRAINT df_bay_active;
ALTER TABLE [booking].service_bay DROP COLUMN is_active;

--changeset lavarapido:booking-018-business-hour-break
--comment: Pausa opcional del dia (ADR-010). Si hay pausa, cae completa dentro del horario
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('booking.business_hour') AND name = 'break_starts_at'
ALTER TABLE [booking].business_hour ADD break_starts_at TIME(0) NULL, break_ends_at TIME(0) NULL;
ALTER TABLE [booking].business_hour ADD CONSTRAINT ck_business_hour_break CHECK (
    (break_starts_at IS NULL AND break_ends_at IS NULL) OR
    (break_starts_at IS NOT NULL AND break_ends_at IS NOT NULL
        AND break_starts_at > opens_at AND break_ends_at < closes_at AND break_ends_at > break_starts_at)
);
