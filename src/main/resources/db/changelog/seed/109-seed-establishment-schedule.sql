--liquibase formatted sql
-- Datos iniciales del negocio: la fila de establishment, el horario semanal y las bahias.
-- Son los mismos valores que la web tenia escritos a mano (datos del negocio y "Horarios y
-- bahias"); desde aqui el admin los cambia en la web y quedan guardados en la base.

--changeset lavarapido:booking-109-seed-establishment
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [booking].establishment
INSERT INTO [booking].establishment (legal_name, trade_name, tax_id, [address], phone, email) VALUES
    (N'Express Car Wash S.A.S.', N'Express Car Wash', N'901.482.930-1',
     N'Calle 127 #19A-48, Bogotá, Colombia', N'+57 312 490 8821', N'contacto@expresscarwash.co');

--changeset lavarapido:booking-109-seed-business-hour
--comment: 1 = lunes ... 7 = domingo. El domingo queda cerrado (is_active = 0)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [booking].business_hour
INSERT INTO [booking].business_hour (day_of_week, opens_at, closes_at, is_active) VALUES
    (1, '07:30', '18:30', 1),
    (2, '07:30', '18:30', 1),
    (3, '07:30', '18:30', 1),
    (4, '07:30', '18:30', 1),
    (5, '07:30', '19:00', 1),
    (6, '08:00', '18:00', 1),
    (7, '08:00', '14:00', 0);

--changeset lavarapido:booking-109-seed-service-bay
--comment: 4 bahias; la 4 empieza en mantenimiento (bay_status 2), como la tenia la web
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [booking].service_bay
INSERT INTO [booking].service_bay (code, name, bay_status_id) VALUES
    (N'BAY-01', N'Bahía 1', 1),
    (N'BAY-02', N'Bahía 2', 1),
    (N'BAY-03', N'Bahía 3', 1),
    (N'BAY-04', N'Bahía 4', 2);
