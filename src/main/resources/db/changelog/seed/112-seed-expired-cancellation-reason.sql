--liquibase formatted sql
-- Motivo con el que el sistema cancela una reserva programada (SCHEDULED) que llego a su hora sin
-- confirmarse. Las confirmadas que nadie inicio quedan NO_SHOW, sin motivo (BookingService.closeExpired).
-- No se ofrece al cancelar a mano.

--changeset lavarapido:booking-112-seed-expired-cancellation-reason
--comment: Motivo EXPIRED para el cierre automatico de reservas vencidas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [booking].cancellation_reason WHERE code = N'EXPIRED'
INSERT INTO [booking].cancellation_reason (code, name, is_customer_fault, display_order) VALUES
    (N'EXPIRED', N'Venció sin confirmarse', 0, 7);
--rollback DELETE FROM [booking].cancellation_reason WHERE code = N'EXPIRED';
