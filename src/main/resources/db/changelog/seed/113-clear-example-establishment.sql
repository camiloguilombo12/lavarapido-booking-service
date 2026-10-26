--liquibase formatted sql
-- E-19: el seed 109 crea la sede con un negocio inventado ("Express Car Wash": razon social, NIT,
-- direccion, telefono y correo) y la web y la app lo mostraban como si fuera real. El 111 ya vacio
-- los datos fiscales y los canales; este quita los que quedaban.
--
-- Cada columna se limpia solo si sigue con el valor de ejemplo: si el admin ya puso los datos reales,
-- no se tocan. razon social, NIT y direccion quedan vacios (la base no acepta NULL ahi) y el admin
-- los completa en Configuracion > Datos del negocio, que los pide como obligatorios. El nombre
-- comercial, que la web no edita, queda con el nombre del sistema; telefono y correo, en NULL.
-- El seed 109 no se cambia: ya corrio en las bases existentes y cambiarlo rompe su checksum.

--changeset lavarapido:booking-113-clear-example-establishment
--comment: Quita nombre, NIT, direccion, telefono y correo de ejemplo del seed 109 (E-19)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM [booking].establishment WHERE deleted_at IS NULL AND (legal_name = N'Express Car Wash S.A.S.' OR trade_name = N'Express Car Wash' OR tax_id = N'901.482.930-1' OR [address] = N'Calle 127 #19A-48, Bogotá, Colombia' OR phone = N'+57 312 490 8821' OR email = N'contacto@expresscarwash.co')
UPDATE [booking].establishment SET
    legal_name = CASE WHEN legal_name = N'Express Car Wash S.A.S.' THEN N'' ELSE legal_name END,
    trade_name = CASE WHEN trade_name = N'Express Car Wash' THEN N'LavaRápido' ELSE trade_name END,
    tax_id     = CASE WHEN tax_id = N'901.482.930-1' THEN N'' ELSE tax_id END,
    [address]  = CASE WHEN [address] = N'Calle 127 #19A-48, Bogotá, Colombia' THEN N'' ELSE [address] END,
    phone      = CASE WHEN phone = N'+57 312 490 8821' THEN NULL ELSE phone END,
    email      = CASE WHEN email = N'contacto@expresscarwash.co' THEN NULL ELSE email END
WHERE deleted_at IS NULL;
--rollback SELECT 1;
