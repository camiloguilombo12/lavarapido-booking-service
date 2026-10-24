--liquibase formatted sql
-- BIZ-09 / E-11: booking.row_version pasa a ser la version del bloqueo optimista (@Version en
-- BookingJpaEntity). Hibernate la sube en cada UPDATE con WHERE row_version = la que leyo; asi una
-- reserva cancelada mientras otro la reprogramaba no "revive".
--
-- El trigger tr_touch_booking tambien la subia en cada UPDATE, y la reserva quedaba dos versiones
-- adelante de lo que cree Hibernate. Desde aqui solo la sube cuando el UPDATE no la cambio (un script
-- a mano, por ejemplo); updated_at lo sigue poniendo siempre.

--changeset lavarapido:booking-045-tr-touch-booking-version splitStatements:false
--comment: El trigger no sube row_version si el UPDATE ya la subio (BIZ-09)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM sys.triggers WHERE name = 'tr_touch_booking'
ALTER TRIGGER [booking].tr_touch_booking
ON [booking].[booking]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;

    UPDATE x
       SET updated_at  = SYSUTCDATETIME(),
           row_version = CASE WHEN i.row_version = d.row_version THEN x.row_version + 1 ELSE x.row_version END
      FROM [booking].[booking] x
      JOIN inserted i ON i.booking_id = x.booking_id
      JOIN deleted d ON d.booking_id = x.booking_id;
END
--rollback ALTER TRIGGER [booking].tr_touch_booking
--rollback ON [booking].[booking]
--rollback AFTER UPDATE
--rollback AS
--rollback BEGIN
--rollback     SET NOCOUNT ON;
--rollback     IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
--rollback     UPDATE x
--rollback        SET updated_at  = SYSUTCDATETIME(),
--rollback            row_version = x.row_version + 1
--rollback       FROM [booking].[booking] x
--rollback       JOIN inserted i ON i.booking_id = x.booking_id;
--rollback END
