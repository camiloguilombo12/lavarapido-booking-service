--liquibase formatted sql
-- Las 11 tablas del booking-service (esquemas catalog y booking). Salen del DDL general del
-- proyecto (lavarapido-6-services-sqlserver.sql, seccion 3). Los cambios de ADR-010 (estado de
-- bahia, pausa del dia) van aparte en 018 para que se vea que se agregaron despues.

--changeset lavarapido:booking-005-schema-catalog
--comment: Esquema catalog (servicios y precios), es de este servicio
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.schemas WHERE name = 'catalog'
CREATE SCHEMA [catalog];

--changeset lavarapido:booking-005-schema-booking
--comment: Esquema booking (horarios, bahias y reservas), es de este servicio
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.schemas WHERE name = 'booking'
CREATE SCHEMA [booking];

--changeset lavarapido:booking-005-service-category
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'catalog' AND t.name = 'service_category'
CREATE TABLE [catalog].service_category (
    service_category_id SMALLINT      IDENTITY(1,1) NOT NULL,
    code          NVARCHAR(30)  NOT NULL,
    name          NVARCHAR(60)  NOT NULL,
    [description] NVARCHAR(200) NULL,
    display_order SMALLINT      NOT NULL CONSTRAINT df_scat_order DEFAULT 0,
    is_active     BIT           NOT NULL CONSTRAINT df_scat_active DEFAULT 1,
    created_at    DATETIME2(3)  NOT NULL CONSTRAINT df_scat_created DEFAULT SYSUTCDATETIME(),
    created_by    BIGINT        NULL,
    updated_at    DATETIME2(3)  NULL,
    updated_by    BIGINT        NULL,
    deleted_at    DATETIME2(3)  NULL,
    deleted_by    BIGINT        NULL,
    row_version   INT           NOT NULL CONSTRAINT df_scat_rv DEFAULT 1,
    CONSTRAINT pk_service_category PRIMARY KEY (service_category_id),
    CONSTRAINT uq_service_category_code UNIQUE (code)
);

--changeset lavarapido:booking-005-service
--comment: Sin columna price a proposito (2FN): el precio depende del par servicio + tipo de vehiculo
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'catalog' AND t.name = 'service'
CREATE TABLE [catalog].[service] (
    service_id          INT            IDENTITY(1,1) NOT NULL,
    code                NVARCHAR(30)   NOT NULL,
    name                NVARCHAR(100)  NOT NULL,
    [description]       NVARCHAR(MAX)  NULL,
    service_category_id SMALLINT       NOT NULL,
    is_active           BIT            NOT NULL CONSTRAINT df_service_active DEFAULT 1,
    created_at          DATETIME2(3)   NOT NULL CONSTRAINT df_service_created DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT         NULL,
    updated_at          DATETIME2(3)   NULL,
    updated_by          BIGINT         NULL,
    deleted_at          DATETIME2(3)   NULL,
    deleted_by          BIGINT         NULL,
    row_version         INT            NOT NULL CONSTRAINT df_service_rv DEFAULT 1,
    CONSTRAINT pk_service PRIMARY KEY (service_id),
    CONSTRAINT uq_service_code UNIQUE (code),
    CONSTRAINT fk_service_category FOREIGN KEY (service_category_id) REFERENCES [catalog].service_category(service_category_id)
);

--changeset lavarapido:booking-005-service-price
--comment: Inmutable (tr_service_price_immutable). Subir un precio cierra la fila (valid_to) e inserta otra
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'catalog' AND t.name = 'service_price'
CREATE TABLE [catalog].service_price (
    service_price_id  BIGINT        IDENTITY(1,1) NOT NULL,
    service_id        INT           NOT NULL,
    vehicle_type_id   SMALLINT      NOT NULL,   -- customer.vehicle_type, sin FK: es de otro servicio
    price             DECIMAL(12,2) NOT NULL,
    estimated_minutes SMALLINT      NOT NULL,
    valid_from        DATE          NOT NULL,
    valid_to          DATE          NULL,
    created_at        DATETIME2(3)  NOT NULL CONSTRAINT df_sprice_created DEFAULT SYSUTCDATETIME(),
    created_by        BIGINT        NULL,
    updated_at        DATETIME2(3)  NULL,
    updated_by        BIGINT        NULL,
    deleted_at        DATETIME2(3)  NULL,
    deleted_by        BIGINT        NULL,
    row_version       INT           NOT NULL CONSTRAINT df_sprice_rv DEFAULT 1,
    CONSTRAINT pk_service_price PRIMARY KEY (service_price_id),
    CONSTRAINT fk_service_price_service FOREIGN KEY (service_id) REFERENCES [catalog].[service](service_id),
    CONSTRAINT ck_service_price_price CHECK (price >= 0),
    CONSTRAINT ck_service_price_minutes CHECK (estimated_minutes > 0),
    CONSTRAINT ck_service_price_range CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE NONCLUSTERED INDEX ux_service_price_current ON [catalog].service_price (service_id, vehicle_type_id) WHERE valid_to IS NULL AND deleted_at IS NULL;
CREATE NONCLUSTERED INDEX ix_service_price_vehicle_type ON [catalog].service_price (vehicle_type_id);

--changeset lavarapido:booking-005-establishment
--comment: El negocio (una sola fila). payment-service solo lee tax_id y logo para los recibos
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'establishment'
CREATE TABLE [booking].establishment (
    establishment_id SMALLINT      IDENTITY(1,1) NOT NULL,
    legal_name       NVARCHAR(150) NOT NULL,
    trade_name       NVARCHAR(150) NOT NULL,
    tax_id           NVARCHAR(30)  NOT NULL,
    [address]        NVARCHAR(200) NOT NULL,
    phone            NVARCHAR(20)  NULL,
    email            NVARCHAR(120) NULL,
    logo_url         NVARCHAR(500) NULL,
    singleton        BIT           NOT NULL CONSTRAINT df_establishment_singleton DEFAULT 1,
    created_at       DATETIME2(3)  NOT NULL CONSTRAINT df_establishment_created DEFAULT SYSUTCDATETIME(),
    created_by       BIGINT        NULL,
    updated_at       DATETIME2(3)  NULL,
    updated_by       BIGINT        NULL,
    deleted_at       DATETIME2(3)  NULL,
    deleted_by       BIGINT        NULL,
    row_version      INT           NOT NULL CONSTRAINT df_establishment_rv DEFAULT 1,
    CONSTRAINT pk_establishment PRIMARY KEY (establishment_id),
    CONSTRAINT uq_establishment_singleton UNIQUE (singleton),
    CONSTRAINT ck_establishment_singleton CHECK (singleton = 1)
);

--changeset lavarapido:booking-005-business-hour
--comment: Horario semanal (1 = lunes ... 7 = domingo). La capacidad es el numero de bahias activas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'business_hour'
CREATE TABLE [booking].business_hour (
    business_hour_id SMALLINT     IDENTITY(1,1) NOT NULL,
    day_of_week      SMALLINT     NOT NULL,
    opens_at         TIME(0)      NOT NULL,
    closes_at        TIME(0)      NOT NULL,
    is_active        BIT          NOT NULL CONSTRAINT df_bh_active DEFAULT 1,
    created_at       DATETIME2(3) NOT NULL CONSTRAINT df_bh_created DEFAULT SYSUTCDATETIME(),
    created_by       BIGINT       NULL,
    updated_at       DATETIME2(3) NULL,
    updated_by       BIGINT       NULL,
    deleted_at       DATETIME2(3) NULL,
    deleted_by       BIGINT       NULL,
    row_version      INT          NOT NULL CONSTRAINT df_bh_rv DEFAULT 1,
    CONSTRAINT pk_business_hour PRIMARY KEY (business_hour_id),
    CONSTRAINT uq_business_hour_day UNIQUE (day_of_week),
    CONSTRAINT ck_business_hour_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_business_hour_range CHECK (closes_at > opens_at)
);

--changeset lavarapido:booking-005-business-hour-exception
--comment: Festivos y jornadas especiales: cerrado todo el dia o con otro horario
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'business_hour_exception'
CREATE TABLE [booking].business_hour_exception (
    business_hour_exception_id INT           IDENTITY(1,1) NOT NULL,
    exception_date             DATE          NOT NULL,
    is_closed                  BIT           NOT NULL CONSTRAINT df_bhe_closed DEFAULT 1,
    opens_at                   TIME(0)       NULL,
    closes_at                  TIME(0)       NULL,
    reason                     NVARCHAR(120) NOT NULL,
    created_at                 DATETIME2(3)  NOT NULL CONSTRAINT df_bhe_created DEFAULT SYSUTCDATETIME(),
    created_by                 BIGINT        NULL,
    updated_at                 DATETIME2(3)  NULL,
    updated_by                 BIGINT        NULL,
    deleted_at                 DATETIME2(3)  NULL,
    deleted_by                 BIGINT        NULL,
    row_version                INT           NOT NULL CONSTRAINT df_bhe_rv DEFAULT 1,
    CONSTRAINT pk_business_hour_exception PRIMARY KEY (business_hour_exception_id),
    CONSTRAINT uq_business_hour_exception_date UNIQUE (exception_date),
    CONSTRAINT ck_bhe_hours CHECK (
        (is_closed = 1 AND opens_at IS NULL AND closes_at IS NULL) OR
        (is_closed = 0 AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND closes_at > opens_at)
    )
);

--changeset lavarapido:booking-005-service-bay
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'service_bay'
CREATE TABLE [booking].service_bay (
    service_bay_id SMALLINT     IDENTITY(1,1) NOT NULL,
    code           NVARCHAR(20) NOT NULL,
    name           NVARCHAR(60) NOT NULL,
    is_active      BIT          NOT NULL CONSTRAINT df_bay_active DEFAULT 1,
    created_at     DATETIME2(3) NOT NULL CONSTRAINT df_bay_created DEFAULT SYSUTCDATETIME(),
    created_by     BIGINT       NULL,
    updated_at     DATETIME2(3) NULL,
    updated_by     BIGINT       NULL,
    deleted_at     DATETIME2(3) NULL,
    deleted_by     BIGINT       NULL,
    row_version    INT          NOT NULL CONSTRAINT df_bay_rv DEFAULT 1,
    CONSTRAINT pk_service_bay PRIMARY KEY (service_bay_id),
    CONSTRAINT uq_service_bay_code UNIQUE (code)
);

--changeset lavarapido:booking-005-booking-status
--comment: Catalogo propio: una FK aqui nunca puede aceptar un estado de pago
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'booking_status'
CREATE TABLE [booking].booking_status (
    booking_status_id SMALLINT     IDENTITY(1,1) NOT NULL,
    code          NVARCHAR(30) NOT NULL,
    name          NVARCHAR(60) NOT NULL,
    is_final      BIT          NOT NULL CONSTRAINT df_bstatus_final DEFAULT 0,
    display_order SMALLINT     NOT NULL CONSTRAINT df_bstatus_order DEFAULT 0,
    is_active     BIT          NOT NULL CONSTRAINT df_bstatus_active DEFAULT 1,
    created_at    DATETIME2(3) NOT NULL CONSTRAINT df_bstatus_created DEFAULT SYSUTCDATETIME(),
    created_by    BIGINT       NULL,
    updated_at    DATETIME2(3) NULL,
    updated_by    BIGINT       NULL,
    deleted_at    DATETIME2(3) NULL,
    deleted_by    BIGINT       NULL,
    row_version   INT          NOT NULL CONSTRAINT df_bstatus_rv DEFAULT 1,
    CONSTRAINT pk_booking_status PRIMARY KEY (booking_status_id),
    CONSTRAINT uq_booking_status_code UNIQUE (code)
);

--changeset lavarapido:booking-005-cancellation-reason
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'cancellation_reason'
CREATE TABLE [booking].cancellation_reason (
    cancellation_reason_id SMALLINT     IDENTITY(1,1) NOT NULL,
    code              NVARCHAR(30) NOT NULL,
    name              NVARCHAR(60) NOT NULL,
    is_customer_fault BIT          NOT NULL CONSTRAINT df_creason_fault DEFAULT 0,
    display_order     SMALLINT     NOT NULL CONSTRAINT df_creason_order DEFAULT 0,
    is_active         BIT          NOT NULL CONSTRAINT df_creason_active DEFAULT 1,
    created_at        DATETIME2(3) NOT NULL CONSTRAINT df_creason_created DEFAULT SYSUTCDATETIME(),
    created_by        BIGINT       NULL,
    updated_at        DATETIME2(3) NULL,
    updated_by        BIGINT       NULL,
    deleted_at        DATETIME2(3) NULL,
    deleted_by        BIGINT       NULL,
    row_version       INT          NOT NULL CONSTRAINT df_creason_rv DEFAULT 1,
    CONSTRAINT pk_cancellation_reason PRIMARY KEY (cancellation_reason_id),
    CONSTRAINT uq_cancellation_reason_code UNIQUE (code)
);

--changeset lavarapido:booking-005-booking
--comment: Sin columna total a proposito: se calcula con booking_service + service_price, asi nunca queda desactualizado
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'booking'
CREATE TABLE [booking].booking (
    booking_id             BIGINT        IDENTITY(1,1) NOT NULL,
    customer_vehicle_id    BIGINT        NOT NULL,   -- customer.customer_vehicle, sin FK: es de otro servicio
    service_bay_id         SMALLINT      NULL,
    scheduled_start        DATETIME2(3)  NOT NULL,
    scheduled_end          DATETIME2(3)  NOT NULL,
    booking_status_id      SMALLINT      NOT NULL,
    booked_by              BIGINT        NOT NULL,   -- security.app_user
    cancellation_reason_id SMALLINT      NULL,
    points_redeemed        INT           NOT NULL CONSTRAINT df_booking_points DEFAULT 0,
    points_discount_amount DECIMAL(12,2) NOT NULL CONSTRAINT df_booking_pdisc DEFAULT 0,
    notes                  NVARCHAR(300) NULL,
    created_at             DATETIME2(3)  NOT NULL CONSTRAINT df_booking_created DEFAULT SYSUTCDATETIME(),
    created_by             BIGINT        NULL,
    updated_at             DATETIME2(3)  NULL,
    updated_by             BIGINT        NULL,
    deleted_at             DATETIME2(3)  NULL,
    deleted_by             BIGINT        NULL,
    row_version            INT           NOT NULL CONSTRAINT df_booking_rv DEFAULT 1,
    CONSTRAINT pk_booking PRIMARY KEY (booking_id),
    CONSTRAINT fk_booking_bay FOREIGN KEY (service_bay_id) REFERENCES [booking].service_bay(service_bay_id),
    CONSTRAINT fk_booking_status FOREIGN KEY (booking_status_id) REFERENCES [booking].booking_status(booking_status_id),
    CONSTRAINT fk_booking_cancellation FOREIGN KEY (cancellation_reason_id) REFERENCES [booking].cancellation_reason(cancellation_reason_id),
    CONSTRAINT ck_booking_range CHECK (scheduled_end > scheduled_start),
    CONSTRAINT ck_booking_cancel CHECK (cancellation_reason_id IS NULL OR booking_status_id = 5),
    CONSTRAINT ck_booking_points CHECK (
        (points_redeemed = 0 AND points_discount_amount = 0) OR
        (points_redeemed > 0 AND points_discount_amount > 0)
    )
);
CREATE NONCLUSTERED INDEX ix_booking_start_status ON [booking].booking (scheduled_start, booking_status_id);
CREATE NONCLUSTERED INDEX ix_booking_customer_vehicle ON [booking].booking (customer_vehicle_id);
CREATE NONCLUSTERED INDEX ix_booking_bay_start ON [booking].booking (service_bay_id, scheduled_start);
CREATE NONCLUSTERED INDEX ix_booking_booked_by ON [booking].booking (booked_by);

--changeset lavarapido:booking-005-booking-service
--comment: El precio se congela por referencia: apunta a una fila inmutable de service_price
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = 'booking' AND t.name = 'booking_service'
CREATE TABLE [booking].booking_service (
    booking_service_id BIGINT       IDENTITY(1,1) NOT NULL,
    booking_id         BIGINT       NOT NULL,
    service_price_id   BIGINT       NOT NULL,
    quantity           SMALLINT     NOT NULL CONSTRAINT df_bsvc_qty DEFAULT 1,
    created_at         DATETIME2(3) NOT NULL CONSTRAINT df_bsvc_created DEFAULT SYSUTCDATETIME(),
    created_by         BIGINT       NULL,
    updated_at         DATETIME2(3) NULL,
    updated_by         BIGINT       NULL,
    deleted_at         DATETIME2(3) NULL,
    deleted_by         BIGINT       NULL,
    row_version        INT          NOT NULL CONSTRAINT df_bsvc_rv DEFAULT 1,
    CONSTRAINT pk_booking_service PRIMARY KEY (booking_service_id),
    CONSTRAINT uq_booking_service UNIQUE (booking_id, service_price_id),
    CONSTRAINT fk_booking_service_booking FOREIGN KEY (booking_id) REFERENCES [booking].booking(booking_id),
    CONSTRAINT ck_booking_service_qty CHECK (quantity > 0),
    CONSTRAINT fk_booking_service_price FOREIGN KEY (service_price_id) REFERENCES [catalog].service_price(service_price_id)
);
CREATE NONCLUSTERED INDEX ix_booking_service_price ON [booking].booking_service (service_price_id);

--changeset lavarapido:booking-005-tr-service-price-immutable splitStatements:false
--comment: Una tarifa ya creada no cambia: solo se puede cerrar (valid_to) o borrar logicamente
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_service_price_immutable'
CREATE TRIGGER [catalog].tr_service_price_immutable
ON [catalog].service_price
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
          FROM inserted i
          JOIN deleted d ON d.service_price_id = i.service_price_id
         WHERE i.service_id <> d.service_id
            OR i.vehicle_type_id <> d.vehicle_type_id
            OR i.price <> d.price
            OR i.estimated_minutes <> d.estimated_minutes
            OR i.valid_from <> d.valid_from
    )
    BEGIN
        THROW 51001, 'service_price rows are immutable: close the row (valid_to) and insert a new one', 1;
    END
END

--changeset lavarapido:booking-005-tr-booking-bay-overlap splitStatements:false
--comment: Red de seguridad de INV-BOOK-006: dos reservas activas no pueden cruzarse en la misma bahia
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_booking_bay_overlap'
CREATE TRIGGER [booking].tr_booking_bay_overlap
ON [booking].booking
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    -- activas = SCHEDULED (1), CONFIRMED (2), IN_PROGRESS (3)
    IF EXISTS (
        SELECT 1
          FROM inserted i
          JOIN [booking].booking b
            ON b.service_bay_id = i.service_bay_id
           AND b.booking_id <> i.booking_id
           AND b.deleted_at IS NULL
           AND b.booking_status_id IN (1, 2, 3)
           AND b.scheduled_start < i.scheduled_end
           AND i.scheduled_start < b.scheduled_end
         WHERE i.service_bay_id IS NOT NULL
           AND i.deleted_at IS NULL
           AND i.booking_status_id IN (1, 2, 3)
    )
    BEGIN
        THROW 51002, 'The service bay is already booked for an overlapping time range', 1;
    END
END

--changeset lavarapido:booking-005-tr-touch-service splitStatements:false
--comment: Trigger que pisa updated_at y row_version. El codigo jamas escribe esas dos columnas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_service'
CREATE TRIGGER [catalog].tr_touch_service
ON [catalog].[service]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = x.row_version + 1
      FROM [catalog].[service] x
      JOIN inserted i ON i.service_id = x.service_id;
END

--changeset lavarapido:booking-005-tr-touch-service-price splitStatements:false
--comment: Trigger que pisa updated_at y row_version. El codigo jamas escribe esas dos columnas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_service_price'
CREATE TRIGGER [catalog].tr_touch_service_price
ON [catalog].[service_price]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = x.row_version + 1
      FROM [catalog].[service_price] x
      JOIN inserted i ON i.service_price_id = x.service_price_id;
END

--changeset lavarapido:booking-005-tr-touch-establishment splitStatements:false
--comment: Trigger que pisa updated_at y row_version. El codigo jamas escribe esas dos columnas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_establishment'
CREATE TRIGGER [booking].tr_touch_establishment
ON [booking].[establishment]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = x.row_version + 1
      FROM [booking].[establishment] x
      JOIN inserted i ON i.establishment_id = x.establishment_id;
END

--changeset lavarapido:booking-005-tr-touch-business-hour splitStatements:false
--comment: Trigger que pisa updated_at y row_version. El codigo jamas escribe esas dos columnas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_business_hour'
CREATE TRIGGER [booking].tr_touch_business_hour
ON [booking].[business_hour]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = x.row_version + 1
      FROM [booking].[business_hour] x
      JOIN inserted i ON i.business_hour_id = x.business_hour_id;
END

--changeset lavarapido:booking-005-tr-touch-business-hour-exception splitStatements:false
--comment: Trigger que pisa updated_at y row_version. El codigo jamas escribe esas dos columnas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_business_hour_exception'
CREATE TRIGGER [booking].tr_touch_business_hour_exception
ON [booking].[business_hour_exception]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = x.row_version + 1
      FROM [booking].[business_hour_exception] x
      JOIN inserted i ON i.business_hour_exception_id = x.business_hour_exception_id;
END

--changeset lavarapido:booking-005-tr-touch-service-bay splitStatements:false
--comment: Trigger que pisa updated_at y row_version. El codigo jamas escribe esas dos columnas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_service_bay'
CREATE TRIGGER [booking].tr_touch_service_bay
ON [booking].[service_bay]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = x.row_version + 1
      FROM [booking].[service_bay] x
      JOIN inserted i ON i.service_bay_id = x.service_bay_id;
END

--changeset lavarapido:booking-005-tr-touch-booking splitStatements:false
--comment: Trigger que pisa updated_at y row_version. El codigo jamas escribe esas dos columnas
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_booking'
CREATE TRIGGER [booking].tr_touch_booking
ON [booking].[booking]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = x.row_version + 1
      FROM [booking].[booking] x
      JOIN inserted i ON i.booking_id = x.booking_id;
END

