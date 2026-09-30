--liquibase formatted sql
-- Catalogo inicial: categorias y los 3 servicios que ya mostraba la web (Basico, Premium, Completo).
--
-- Precios del automovil acordados con el equipo: Basico $20.000 (45 min), Premium $35.000 (75 min),
-- Completo $50.000 (120 min). Para los demas tipos se tomo el size_factor de customer.vehicle_type
-- (moto x0.80, SUV x1.15, camioneta x1.20, camion x1.35) redondeado al millar. Desde aqui el
-- admin los cambia en "Gestion > Servicios"; cada cambio crea una fila nueva de service_price.
--
-- Los vehicle_type_id son los fijos del seed 102 del customer-service:
--   1 CAR, 2 SEDAN, 3 SUV, 4 PICKUP, 5 TRUCK, 6 MOTO

--changeset lavarapido:booking-103-seed-service-category
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [catalog].service_category
INSERT INTO [catalog].service_category (code, name, [description], display_order) VALUES
    (N'LAVADO',     N'Lavado',     N'Lavado exterior e interior del vehiculo', 1),
    (N'POLICHADO',  N'Polichado',  N'Brillo y proteccion de la pintura',       2),
    (N'DETALLADO',  N'Detallado',  N'Limpieza a fondo por partes',             3),
    (N'INTERIOR',   N'Interior',   N'Tapiceria, aspirado y desinfeccion',      4),
    (N'PAQUETES',   N'Paquetes',   N'Combos de varios servicios (ADR-010)',    5);

--changeset lavarapido:booking-103-seed-service
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [catalog].[service]
INSERT INTO [catalog].[service] (code, name, [description], service_category_id)
SELECT v.code, v.name, v.description, c.service_category_id
FROM (VALUES
    (N'BASIC',   N'Básico',   N'Lavado exterior completo, aspirado básico y limpieza de vidrios.',        N'LAVADO'),
    (N'PREMIUM', N'Premium',  N'Todo lo del Básico, lavado de motor y cera líquida protectora.',          N'LAVADO'),
    (N'FULL',    N'Completo', N'Todo lo del Premium, encerado a mano y detallado de interiores.',         N'PAQUETES')
) AS v(code, name, description, category)
JOIN [catalog].service_category c ON c.code = v.category;

--changeset lavarapido:booking-103-seed-service-price
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM [catalog].service_price
INSERT INTO [catalog].service_price (service_id, vehicle_type_id, price, estimated_minutes, valid_from)
SELECT s.service_id, v.vehicle_type_id, v.price, v.minutes, CAST('2026-01-01' AS DATE)
FROM (VALUES
    (N'BASIC',   1, 20000,  45), (N'BASIC',   2, 20000,  45), (N'BASIC',   3, 23000,  45),
    (N'BASIC',   4, 24000,  45), (N'BASIC',   5, 27000,  45), (N'BASIC',   6, 16000,  45),
    (N'PREMIUM', 1, 35000,  75), (N'PREMIUM', 2, 35000,  75), (N'PREMIUM', 3, 40000,  75),
    (N'PREMIUM', 4, 42000,  75), (N'PREMIUM', 5, 47000,  75), (N'PREMIUM', 6, 28000,  75),
    (N'FULL',    1, 50000, 120), (N'FULL',    2, 50000, 120), (N'FULL',    3, 58000, 120),
    (N'FULL',    4, 60000, 120), (N'FULL',    5, 68000, 120), (N'FULL',    6, 40000, 120)
) AS v(code, vehicle_type_id, price, minutes)
JOIN [catalog].[service] s ON s.code = v.code;
