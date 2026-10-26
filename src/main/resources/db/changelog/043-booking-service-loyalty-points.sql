--liquibase formatted sql
-- A-09: los puntos de fidelización de cada línea de reserva se congelan al reservar, como el precio.
-- Antes se leían del catálogo vigente y cambiaban si el administrador editaba el servicio.

--changeset lavarapido:booking-043-booking-service-loyalty-points
--comment: Puntos que valía el servicio al reservar
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('booking.booking_service') AND name = 'loyalty_points'
ALTER TABLE [booking].booking_service ADD loyalty_points INT NULL;
--rollback ALTER TABLE [booking].booking_service DROP COLUMN loyalty_points;

--changeset lavarapido:booking-043-booking-service-loyalty-points-backfill
--comment: Las líneas existentes toman los puntos vigentes del servicio (el único dato que hay)
UPDATE bs
SET bs.loyalty_points = s.loyalty_points
FROM [booking].booking_service bs
JOIN [catalog].service_price sp ON sp.service_price_id = bs.service_price_id
JOIN [catalog].service s ON s.service_id = sp.service_id
WHERE bs.loyalty_points IS NULL;
--rollback SELECT 1;
