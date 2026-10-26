--liquibase formatted sql
-- Motivo con el que el sistema cancela una reserva con pago digital cuando llega su plazo
-- (payment_deadline) sin comprobante (PaymentDeadlineJob). Como EXPIRED, nadie lo elige a mano:
-- ni el cliente ni el admin (CancellationReason.selectableBy). notification-service muestra el texto.

--changeset lavarapido:booking-117-seed-payment-timeout-cancellation-reason
--comment: Motivo PAYMENT_TIMEOUT para la cancelación automática por falta de pago
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [booking].cancellation_reason WHERE code = N'PAYMENT_TIMEOUT'
INSERT INTO [booking].cancellation_reason (code, name, is_customer_fault, display_order) VALUES
    (N'PAYMENT_TIMEOUT', N'No se subió el comprobante de pago a tiempo', 0, 8);
--rollback DELETE FROM [booking].cancellation_reason WHERE code = N'PAYMENT_TIMEOUT';
