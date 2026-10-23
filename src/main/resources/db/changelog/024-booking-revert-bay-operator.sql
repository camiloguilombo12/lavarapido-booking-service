--liquibase formatted sql
-- Revierte 021 (operario fijo por bahia): decidimos que no cumple ADR-010 (el operario es por
-- ejecucion, no por bahia). Si el 021 nunca se corrio en este ambiente, la precondicion lo salta.

--changeset lavarapido:booking-024-revert-bay-operator
--comment: Quita operator_id de service_bay
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('booking.service_bay') AND name = 'operator_id'
ALTER TABLE [booking].service_bay DROP COLUMN operator_id;
--rollback ALTER TABLE [booking].service_bay ADD operator_id BIGINT NULL;
