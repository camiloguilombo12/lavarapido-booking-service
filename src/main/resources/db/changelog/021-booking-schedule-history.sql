--liquibase formatted sql
-- Historial de cambios de horario, excepciones y bahias (pantalla "Horarios y Bahias" del admin).
-- Es un log de solo insercion: no tiene row_version ni soft delete porque no se edita ni se borra.

--changeset lavarapido:booking-021-create-schedule-history dbms:mssql
--comment: Un registro por cada cambio de horario semanal, excepcion o bahia
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'schedule_history'
CREATE TABLE [booking].schedule_history (
    schedule_history_id BIGINT       IDENTITY(1,1) NOT NULL,
    -- WEEK (horario semanal), EXCEPTION (dia especial/festivo) o BAY (bahia)
    entity_type         NVARCHAR(20) NOT NULL,
    title               NVARCHAR(200) NOT NULL,
    detail              NVARCHAR(300) NULL,
    changed_at          DATETIME2(3) NOT NULL CONSTRAINT df_schedhist_changed_at DEFAULT SYSUTCDATETIME(),
    -- security.app_user; sin FK porque booking-service no conoce ese esquema (ADR-009)
    changed_by          BIGINT       NULL,
    CONSTRAINT pk_schedule_history PRIMARY KEY (schedule_history_id),
    CONSTRAINT ck_schedhist_entity_type CHECK (entity_type IN (N'WEEK', N'EXCEPTION', N'BAY'))
);

CREATE INDEX ix_schedule_history_changed_at ON [booking].schedule_history (changed_at DESC);
--rollback DROP TABLE [booking].schedule_history;
