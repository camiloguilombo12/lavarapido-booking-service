# booking-service

Reservas, catálogo, horario, bahías y agenda del lavadero. Administra los
esquemas `catalog` y `booking` (ADR-009) y publica los eventos de la reserva en
RabbitMQ (ADR-004, cross-cutting.md §7).

## Qué hace este servicio

- **Catálogo** (esquema `catalog`): categorías, servicios y precios por tipo de
  vehículo. El precio es inmutable: una línea de reserva apunta a la tarifa con
  la que se cobró, así un cambio de precio no reescribe reservas viejas.
- **Horario y bahías** (esquema `booking`): horario semanal (1 = lunes … 7 =
  domingo) con pausa de almuerzo opcional (ADR-010), excepciones por fecha
  (festivos), y las bahías con su estado `ACTIVE` / `MAINTENANCE` / `INACTIVE`.
- **Disponibilidad y reservas**: valida que el slot esté libre y que el
  vehículo sea del cliente (preguntándole al customer-service por REST,
  ADR-012). Si la hora pedida está ocupada responde `409 SLOT_UNAVAILABLE` con
  alternativas del mismo día (RF-006). El frontend pinta la reserva desde la
  respuesta; nadie recalcula precios.

## Endpoints

Públicos (sin token):

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/catalog/categories` | Categorías del catálogo |
| GET | `/api/v1/catalog/services?vehicleTypeId=` | Servicios activos; con tipo de vehículo trae su tarifa |
| GET | `/api/v1/schedule/business-hours` | Los 7 días de la semana |
| GET | `/api/v1/schedule/exceptions?from=` | Excepciones de horario |
| GET | `/api/v1/establishment` | Datos de la sede |

Cliente (token con rol `CLIENT`; un admin o un operario recibe `403`, BIZ-12):

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/bookings/availability?date=&vehicleTypeId=&serviceIds=` | Horas libres de un día (`excludeBookingId` al reprogramar). También la usa el admin |
| GET | `/api/v1/bookings/cancellation-reasons` | Motivos de cancelación. También los usa el admin |
| POST | `/api/v1/bookings` | Crear una reserva (201); `preferredOperatorId` opcional (RF-010) |
| GET | `/api/v1/bookings/me` | Mis reservas |
| GET | `/api/v1/bookings/{id}` | Una reserva propia (404 si es de otro) |
| PUT | `/api/v1/bookings/{id}` | Reprogramar fecha y hora (RF-007, solo antes de empezar). Si manda otros `serviceIds`: `409 BOOKING_SERVICES_LOCKED` (BIZ-01); sin `serviceIds` o con los mismos, sigue igual |
| POST | `/api/v1/bookings/{id}/cancel` | Cancelar; cuerpo opcional `{reasonCode}`: el cliente elige CUSTOMER_REQUEST (por defecto), SCHEDULE_CONFLICT, VEHICLE_ISSUE u OTHER; otro motivo responde 400 `INVALID_CANCELLATION_REASON` (A-20) |

Admin (`/api/v1/admin/**`, exige rol `ADMIN` en el token):

| Método | Ruta | Uso |
|---|---|---|
| GET / POST | `/api/v1/admin/bookings` | Listar (filtros `from`, `to`, `status`) / reservar a nombre de un cliente |
| GET | `/api/v1/admin/bookings/day-summary?date=` | Reservas del día frente al día anterior |
| GET / PUT | `/api/v1/admin/bookings/{id}` | Ver / reprogramar una reserva (el admin sí puede cambiar los servicios) |
| PATCH | `/api/v1/admin/bookings/{id}/status` | `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `NO_SHOW` o `CANCELLED` (con `reasonCode`) |
| GET / POST / PUT / DELETE | `/api/v1/admin/catalog/services[/{id}]` | Gestionar servicios (+ PATCH `/{id}/status`). Precio en 0 o negativo: `400 INVALID_PRICE` (BIZ-04) |
| GET / PUT | `/api/v1/admin/establishment` | Datos legales, fiscales y canales del negocio |
| PUT | `/api/v1/admin/schedule/business-hours` | Guardar la semana completa |
| POST / PUT / DELETE | `/api/v1/admin/schedule/exceptions[/{id}]` | Excepciones de horario |
| GET / POST / PUT / DELETE | `/api/v1/admin/bays[/{id}]` | Bahías y su estado |
| GET | `/api/v1/admin/schedule/history` | Historial de cambios de horario, excepciones y bahías |

Cambiar la semana, crear, editar o quitar una excepción, o poner una bahía en `MAINTENANCE` o
`INACTIVE`, revisa antes las reservas activas: si alguna queda por fuera, `409
SCHEDULE_CONFLICTS_BOOKINGS` con `conflictingBookings` (cuántas) y `bookingCodes` (las primeras 20),
y no se guarda nada (BIZ-10). Borrar una bahía con reservas por delante es `409 BAY_HAS_BOOKINGS`.

Operario (`/api/v1/operator/bookings`): rol `OPERATOR` o `ADMIN` **y** la llave interna
`X-Internal-Key`. Solo las llama operations-service, que valida que la reserva esté asignada al
operario; el gateway no las publica (E-01).

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/operator/bookings?date=&to=` | Reservas del día (sin canceladas ni no asistió) |
| GET | `/api/v1/operator/bookings/{id}` | Una reserva |
| PATCH | `/api/v1/operator/bookings/{id}/status` | `IN_PROGRESS` (desde 15 minutos antes; si no, `409 SERVICE_TOO_EARLY`) o `COMPLETED` |

Entre servicios (`/internal/v1/bookings`, solo con `X-Internal-Key`; nunca con el token de un
usuario, ni siquiera el de un admin):

| Método | Ruta | Uso |
|---|---|---|
| GET | `/internal/v1/bookings?from=&to=` | Reservas de un rango, sin datos del vehículo (operations, RF-008/010) |
| GET | `/internal/v1/bookings/{id}` | Una reserva |
| GET | `/internal/v1/bookings/active-count?customerVehicleId=` | `{"customerVehicleId": 15, "activeBookings": 2}`: en curso, o programadas/confirmadas que no han terminado. customer-service lo pregunta antes de retirar un vehículo (BIZ-08) |
| POST | `/internal/v1/bookings/{id}/no-show` | operations cierra como `NO_SHOW` una confirmada vencida que nunca tuvo operario |

La respuesta de una reserva es `BookingResponse`: ids, código (`RES-000123`, sale del id), estado,
horas locales y en UTC, bahía, vehículo, servicios con el precio cobrado (`service_price_id`) y sus
puntos, subtotal/total y `changeable` (si todavía se puede modificar). No hay columna `total` en la
base: se deriva de las líneas.

## Reglas que cuida

- **Precio y puntos congelados.** Una línea apunta a la tarifa con la que se cobró y guarda los
  puntos de ese momento (A-09). Al reprogramar se conserva la tarifa solo si el vehículo sigue
  siendo del mismo tipo; si cambió (de moto a camioneta), va la tarifa vigente del tipo nuevo (BIZ-06).
- **Agenda.** Crear, reprogramar, cancelar, cambiar el estado, el avance del operario y los cambios
  de horario y bahías toman el candado de la agenda (`sp_getapplock`); el trigger
  `tr_booking_bay_overlap` es la última red contra dos reservas cruzadas en la misma bahía.
- **Bloqueo optimista.** `booking.row_version` es el `@Version` de la reserva: si otra petición la
  cambió entre la lectura y el guardado, no se pisa y responde `409 CONFLICT` (BIZ-09, E-11).
- **Sin cierre automático.** Las reservas nacen y se reprograman `CONFIRMED`; no hay tarea que
  cancele programadas vencidas (B-07). Las confirmadas que nadie inició y no tuvieron operario las
  cierra operations-service como `NO_SHOW`, pasado `BOOKING_EXPIRY_GRACE_MINUTES`.

## Eventos

Publica en el exchange `carwash.events` (solo con `MESSAGING_ENABLED=true`), con el sobre
`{eventId, eventType, aggregateId, occurredAt, version, payload}`:

| Evento | Routing key | Cuándo |
|---|---|---|
| `BookingConfirmed` | `booking.confirmed` | Al crear (con `preferredOperatorId` si el cliente eligió), al reprogramar y cuando el admin la confirma |
| `BookingCancelled` | `booking.cancelled` | Al cancelar; `reason` es el nombre del motivo y `reasonCode` su código (A-20, OPS-10) |
| `BookingStatusChanged` | `booking.status_changed` | El admin la pasó a en curso, completada o no asistió (`status` en el payload, A-11) |

El payload lleva `bookingId`, `bookingCode`, `customerUserId`, `customerVehicleId`,
`scheduledStart` y `serviceBayId`. El evento sale **después** del commit de la transacción
(cross-cutting.md §6). Sin broker el servicio funciona igual: los eventos solo quedan en el log.

## Arquitectura

Hexagonal (ADR-007), misma base que los demás servicios:

```
domain/        modelos, invariantes (AvailabilityPolicy) y puertos
application/   casos de uso: BookingService, CatalogService, ScheduleService
infrastructure/ adapters: REST (web), RabbitMQ, customer-service (REST), JPA
```

`CustomerServiceDirectory` reenvía el Bearer de quien llama: un cliente solo ve sus vehículos y
`/admin/vehicles` exige ADMIN u OPERATOR. Si customer no responde, la operación falla con
`503 CUSTOMER_SERVICE_UNAVAILABLE` y nunca se reserva sin validar el vehículo (INV-BOOK-002). Si
customer responde con un 4xx, ese rechazo se pasa como `403 FORBIDDEN`, `409` o `400` con su mismo
`code` (por ejemplo `CUSTOMER_NOT_PROVISIONED`), no como 503 (BIZ-13).

**Errores.** Todo sale como `application/problem+json` con un `code` estable que la web y la app
traducen. Si la base no responde (sin conexión, tiempo agotado) es `503
BOOKING_SERVICE_UNAVAILABLE`; un choque de datos (índice único, CHECK, trigger, bloqueo) es `409
CONFLICT`; cualquier otro error de base es `500 INTERNAL_ERROR` (E-18).

## Base de datos

Liquibase contra el SQL Server del host (la misma instancia de los seis
servicios, una tabla de changelog por servicio):

- `005-create-booking-schemas.sql` — esquemas `catalog` y `booking` (11 tablas) y triggers
- `018-booking-adr-010.sql` — estado de bahía y pausa del día
- `021-booking-schedule-history.sql` — historial de cambios de horario, excepciones y bahías
- `022-booking-catalog-loyalty-points.sql` — puntos de fidelización por servicio
- `024-booking-revert-bay-operator.sql` — quita `operator_id` de `service_bay`
- `032-booking-establishment-business-data.sql` — datos legales, fiscales y canales de la sede
- `043-booking-service-loyalty-points.sql` — congela los puntos de fidelización de cada línea al reservar (A-09); las líneas existentes toman los puntos vigentes. Rollback: quita la columna
- `044-booking-service-price-positive.sql` — la tarifa vigente vale más de 0 (`ck_service_price_positive`, BIZ-04). Si ya había alguna en 0 se crea `WITH NOCHECK` para no romper el despliegue
- `045-booking-row-version-optimistic-lock.sql` — `tr_touch_booking` ya no sube `row_version` cuando el UPDATE la subió (Hibernate, `@Version`, BIZ-09). Rollback: el trigger anterior
- `seed/103-seed-catalog.sql` — categorías, servicios y tarifas
- `seed/104-seed-booking-catalogs.sql` — estados y motivos de cancelación
- `seed/109-seed-establishment-schedule.sql` — sede, semana y bahías
- `seed/110` y `seed/111` — datos de ejemplo del negocio y su limpieza
- `seed/112-seed-expired-cancellation-reason.sql` — motivo del sistema `EXPIRED`
- `seed/113-clear-example-establishment.sql` — quita la razón social, NIT, dirección, teléfono y correo inventados ("Express Car Wash") que dejaba el 109; solo los que sigan siendo de ejemplo (E-19)

`DATABASECHANGELOG_BOOKING`. Si el esquema cambió y la base quedó vieja, lo más
simple es recrear los dos esquemas y limpiar esa tabla para que vuelva a migrar
desde cero (es una base de desarrollo).

## Correr

```bash
# desde el repo, en la raíz del workspace (las variables salen de lavarapido-infra/.env)
.\mvnw.cmd spring-boot:run
```

Puerto `3003`. Pruebas: `./mvnw test`, 95 al 24-10-2026 con todas las ramas del 23-10 fusionadas,
sin base de datos.

| Variable | Para qué |
|---|---|
| `INTERNAL_API_KEY` | Llave `X-Internal-Key` de `/internal/**` y de las rutas del operario. Vacía: esas rutas responden 401/403 |
| `CUSTOMER_SERVICE_URL` | Base de customer-service con `/api/v1` (por defecto `http://localhost:3002/api/v1`) |
| `CUSTOMER_SERVICE_TIMEOUT` | Tiempo máximo de esa llamada (`3s`) |
| `BOOKING_ZONE` | Zona horaria del lavadero (`America/Bogota`) |
| `BOOKING_SLOT_MINUTES` | Cada cuánto se ofrece una hora (`30`) |
| `BOOKING_MAX_DAYS_AHEAD` | Hasta cuántos días adelante se reserva (`60`) |
| `BOOKING_EXPIRY_GRACE_MINUTES` | Margen después de la hora de fin para cerrar una confirmada como no asistió (`60`) |
| `MESSAGING_ENABLED` | `true` publica los eventos en RabbitMQ (por defecto `false`, solo log) |
| `CORS_ALLOWED_ORIGINS`, `PORT` | `http://localhost:4200,http://localhost:8081` y `3003` |

El resto (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_*`, `RABBITMQ_*`) se comparte con los demás
servicios y está documentado en `lavarapido-infra/.env.example`. `BOOKING_EXPIRY_CHECK_*` ya no
existe (B-07).

Documentación de la API (Swagger) en el perfil `dev`:
`http://localhost:3003/swagger-ui.html`.

## Proyecto LavaRápido

Este repositorio es parte de LavaRápido (Vehicle Washing), proyecto formativo del Tecnólogo en
Análisis y Desarrollo de Software (ADSO), ficha 3145556, SENA Regional Huila.

| Recurso | Enlace |
|---|---|
| Documentación y manuales (ES/EN, MD · PDF · Word) | [lavarapido-docs](https://github.com/BrayanCABRERA123/lavarapido-docs#readme) |
| Manual técnico | [09_MANUALES/ES/MD/manual-tecnico.md](https://github.com/BrayanCABRERA123/lavarapido-docs/blob/main/09_MANUALES/ES/MD/manual-tecnico.md) |
| Manual de instalación | [09_MANUALES/ES/MD/manual-de-instalacion.md](https://github.com/BrayanCABRERA123/lavarapido-docs/blob/main/09_MANUALES/ES/MD/manual-de-instalacion.md) |
| Levantar todo el sistema | [lavarapido-infra](https://github.com/BrayanCABRERA123/lavarapido-infra) (`docker compose --profile app up -d`) |
| Aplicación desplegada | https://lavarapidovehicular.northcentralus.cloudapp.azure.com |

**Ramas y commits:** se trabaja en `develop` con ramas `feature/`, `fix/` y `docs/` que se
fusionan con `git merge --no-ff`; los commits siguen Conventional Commits
(`feat(scope): ...`, `fix(scope): ...`). Detalle en la
[guía de git del equipo](https://github.com/BrayanCABRERA123/lavarapido-docs#guía-de-git-del-equipo).

**Equipo:** Brayan Estiven Patiño Cabrera, Juan Camilo Guilombo Osorio y Merari Urbano Quintero.
