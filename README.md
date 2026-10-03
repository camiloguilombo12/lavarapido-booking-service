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
| GET | `/api/v1/bookings/availability?date=&vehicleTypeId=&serviceIds=` | Horas libres de un día (`excludeBookingId` al reprogramar) |
| GET | `/api/v1/bookings/cancellation-reasons` | Motivos de cancelación |

Cliente (con su token):

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/v1/bookings` | Crear una reserva (201) |
| GET | `/api/v1/bookings/me` | Mis reservas |
| GET | `/api/v1/bookings/{id}` | Una reserva propia (404 si es de otro) |
| PUT | `/api/v1/bookings/{id}` | Reprogramar (RF-007, solo antes de empezar) |
| POST | `/api/v1/bookings/{id}/cancel` | Cancelar |

Admin (`/api/v1/admin/**`, exige rol `ADMIN` en el token):

| Método | Ruta | Uso |
|---|---|---|
| GET / POST | `/api/v1/admin/bookings` | Listar (filtros `from`, `to`, `status`) / reservar a nombre de un cliente |
| GET / PUT | `/api/v1/admin/bookings/{id}` | Ver / reprogramar una reserva |
| PATCH | `/api/v1/admin/bookings/{id}/status` | `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `NO_SHOW`… |
| GET / POST / PUT / DELETE | `/api/v1/admin/catalog/services[/{id}]` | Gestionar servicios (+ PATCH `/{id}/status`) |
| PUT | `/api/v1/admin/schedule/business-hours` | Guardar la semana completa |
| POST / PUT / DELETE | `/api/v1/admin/schedule/exceptions[/{id}]` | Excepciones de horario |
| GET / POST / PUT / DELETE | `/api/v1/admin/bays[/{id}]` | Bahías y su estado |

La respuesta de una reserva es `BookingResponse`: ids, código (`LV-…`), estado,
horas locales y en UTC, bahía, vehículo, servicios con el precio cobrado
(`service_price_id`), subtotal/total y `changeable` (si todavía se puede
modificar). No hay columna `total` en la base: se deriva de las líneas.

## Eventos

Publica en el exchange `carwash.events` (solo con `MESSAGING_ENABLED=true`):

| Evento | Routing key |
|---|---|
| Reserva creada | `booking.created` |
| Reserva confirmada | `booking.confirmed` |
| Reserva modificada | `booking.modified` |
| Reserva cancelada | `booking.cancelled` |

El evento sale **después** del commit de la transacción (cross-cutting.md §6).
Sin broker el servicio funciona igual: los eventos solo quedan en el log.

## Arquitectura

Hexagonal (ADR-007), misma base que los demás servicios:

```
domain/        modelos, invariantes (AvailabilityPolicy) y puertos
application/   casos de uso: BookingService, CatalogService, ScheduleService
infrastructure/ adapters: REST (web), RabbitMQ, customer-service (REST), JPA
```

`CustomerServiceDirectory` reenvía el Bearer de quien llama: un cliente solo ve
sus vehículos y `/admin/vehicles` exige ADMIN; si el vecino no responde la
operación falla con `503 CUSTOMER_SERVICE_UNAVAILABLE`, nunca se reserva sin
validar el vehículo (INV-BOOK-002).

## Base de datos

Liquibase contra el SQL Server del host (la misma instancia de los seis
servicios, una tabla de changelog por servicio):

- `005-create-booking-schemas.sql` — esquemas `catalog` y `booking` (11 tablas)
- `018-booking-adr-010.sql` — estado de bahía y pausa del día
- `seed/103-seed-catalog.sql` — categorías, servicios y tarifas
- `seed/104-seed-booking-catalogs.sql` — estados y motivos de cancelación
- `seed/109-seed-establishment-schedule.sql` — sede, semana y bahías

`DATABASECHANGELOG_BOOKING`. Si el esquema cambió y la base quedó vieja, lo más
simple es recrear los dos esquemas y limpiar esa tabla para que vuelva a migrar
desde cero (es una base de desarrollo).

## Correr

```bash
# desde el repo, en la raíz del workspace (las variables salen de lavarapido-infra/.env)
.\mvnw.cmd spring-boot:run
```

Puerto `3003`. Variables propias: `CUSTOMER_SERVICE_URL`,
`CUSTOMER_SERVICE_TIMEOUT`, `BOOKING_ZONE` (default `America/Bogota`),
`BOOKING_SLOT_MINUTES` (30), `BOOKING_MAX_DAYS_AHEAD` (60), `MESSAGING_ENABLED`.
El resto (`DB_URL`, `DB_PASSWORD`, `JWT_SECRET`, `RABBITMQ_*`) se comparte con
los cinco servicios y está documentado en `lavarapido-infra/.env.example`.

Documentación de la API (Swagger) en el perfil `dev`:
`http://localhost:3003/swagger-ui.html`.