--liquibase formatted sql
-- Estados de reserva (codigos en ingles, ADR-010) y motivos de cancelacion.
-- Los ids de estado van fijos: el CHECK ck_booking_cancel y el trigger de traslape usan
-- 5 = CANCELLED y 1..3 = activas.

--changeset lavarapido:booking-104-seed-booking-status
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [booking].booking_status
SET IDENTITY_INSERT [booking].booking_status ON;
INSERT INTO [booking].booking_status (booking_status_id, code, name, is_final, display_order) VALUES
    (1, N'SCHEDULED',   N'Agendada',     0, 1),
    (2, N'CONFIRMED',   N'Confirmada',   0, 2),
    (3, N'IN_PROGRESS', N'En ejecución', 0, 3),
    (4, N'COMPLETED',   N'Finalizada',   1, 4),
    (5, N'CANCELLED',   N'Cancelada',    1, 5),
    (6, N'NO_SHOW',     N'No asistió',   1, 6);
SET IDENTITY_INSERT [booking].booking_status OFF;

--changeset lavarapido:booking-104-seed-cancellation-reason
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [booking].cancellation_reason
INSERT INTO [booking].cancellation_reason (code, name, is_customer_fault, display_order) VALUES
    (N'CUSTOMER_REQUEST',  N'El cliente la canceló',          0, 1),
    (N'SCHEDULE_CONFLICT', N'El cliente no puede a esa hora', 0, 2),
    (N'VEHICLE_ISSUE',     N'Problema con el vehículo',       0, 3),
    (N'SHOP_ISSUE',        N'Imprevisto del lavadero',        0, 4),
    (N'LATE_ARRIVAL',      N'El cliente llegó tarde',         1, 5),
    (N'OTHER',             N'Otro motivo',                    0, 6);
