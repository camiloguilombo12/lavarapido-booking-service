--liquibase formatted sql
-- Puntos de fidelidad que acumula el cliente al pagar una reserva con este servicio (admin,
-- Gestion > Servicios). Cuantos puntos valen los decide el admin por servicio, no una tasa fija.

--changeset lavarapido:booking-022-add-service-loyalty-points
--comment: Puntos que acumula el cliente al completar este servicio
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('catalog.service') AND name = 'loyalty_points'
ALTER TABLE [catalog].service ADD loyalty_points INT NOT NULL CONSTRAINT df_service_loyalty_points DEFAULT 0;
--rollback ALTER TABLE [catalog].service DROP COLUMN loyalty_points;
