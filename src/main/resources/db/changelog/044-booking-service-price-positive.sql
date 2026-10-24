--liquibase formatted sql
-- BIZ-04: la tarifa vigente de un servicio tiene que valer mas de 0. Con precio 0 la reserva no se
-- puede pagar (payment no acepta montos en 0) y queda colgada. El dominio ya lo valida
-- (PriceDefinition, INVALID_PRICE); esto es la red de la base.
--
-- Solo cuenta la tarifa vigente: una fila cerrada (valid_to) o borrada puede seguir en 0, porque
-- reservas viejas la apuntan y service_price es inmutable. Asi, cerrar una tarifa en 0 para
-- corregirla sigue funcionando.
--
-- Si al desplegar ya hay alguna tarifa vigente en 0, la regla se crea WITH NOCHECK: no rompe el
-- despliegue ni esas filas, pero toda tarifa nueva se valida. El admin la corrige poniendole un
-- precio en "Gestion > Servicios" (eso cierra la fila y crea otra).

--changeset lavarapido:booking-044-service-price-positive splitStatements:false
--comment: La tarifa vigente vale mas de 0 (BIZ-04)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'ck_service_price_positive'
IF EXISTS (SELECT 1 FROM [catalog].service_price WHERE price <= 0 AND valid_to IS NULL AND deleted_at IS NULL)
    ALTER TABLE [catalog].service_price WITH NOCHECK
        ADD CONSTRAINT ck_service_price_positive CHECK (price > 0 OR valid_to IS NOT NULL OR deleted_at IS NOT NULL);
ELSE
    ALTER TABLE [catalog].service_price WITH CHECK
        ADD CONSTRAINT ck_service_price_positive CHECK (price > 0 OR valid_to IS NOT NULL OR deleted_at IS NOT NULL);
--rollback ALTER TABLE [catalog].service_price DROP CONSTRAINT ck_service_price_positive;
