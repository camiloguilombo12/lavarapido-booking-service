--liquibase formatted sql
-- B y D (contrato de pago de la reserva, 26-10-2026): cómo va a pagar el cliente y hasta cuándo tiene
-- para reportar el pago digital.
--  - payment_mode: DIGITAL (QR + comprobante) o CASH (efectivo en el lavadero). NULL en las reservas
--    anteriores al cambio y en las que crea el admin sin decir el modo.
--  - payment_deadline: hasta cuándo el cliente puede subir el comprobante de una reserva DIGITAL
--    (min(creación + ventana, inicio)). NULL si no tiene plazo.
--  - payment_reported_at: cuándo llegó el comprobante (o el pago aprobado) desde payment-service.
-- NVARCHAR y no VARCHAR: Hibernate usa use_nationalized_character_data (el incidente de la 048).

--changeset lavarapido:booking-049-booking-payment-columns
--comment: Modo de pago, plazo para reportarlo y cuándo se reportó
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('booking.booking') AND name = 'payment_mode'
ALTER TABLE [booking].booking ADD
    payment_mode        NVARCHAR(10) NULL CONSTRAINT ck_booking_payment_mode CHECK (payment_mode IN (N'DIGITAL', N'CASH')),
    payment_deadline    DATETIME2(3) NULL,
    payment_reported_at DATETIME2(3) NULL;
--rollback ALTER TABLE [booking].booking DROP CONSTRAINT ck_booking_payment_mode;
--rollback ALTER TABLE [booking].booking DROP COLUMN payment_mode, payment_deadline, payment_reported_at;

--changeset lavarapido:booking-049-booking-payment-pending-index
--comment: Solo las DIGITAL sin reporte, que son las que revisa PaymentDeadlineJob
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID('booking.booking') AND name = 'ix_booking_payment_pending'
CREATE NONCLUSTERED INDEX ix_booking_payment_pending ON [booking].booking (payment_deadline)
    INCLUDE (booking_status_id, deleted_at)
    WHERE payment_mode = N'DIGITAL' AND payment_reported_at IS NULL;
--rollback DROP INDEX ix_booking_payment_pending ON [booking].booking;
