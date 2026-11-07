# StyleTrack – Salon Management API (Spring Boot + PostgreSQL)

Back-office API for the StyleTrack web app. Roles: **Owner/Admin**, **Staff**, **Stylist**.
Java 21 · Spring Boot 3.3 · Spring Security (JWT) · Spring Data JPA · PostgreSQL · Lombok · springdoc (Swagger).

## Run it

**Docker (recommended / deploy)**
```bash
cp .env.example .env        # then edit DB_PASSWORD, JWT_SECRET (32+ chars), ADMIN_PASSWORD
docker compose up -d --build
# API: http://localhost:8080   health: /actuator/health
```

**Local dev**
```bash
# needs a PostgreSQL db named "styletrack" (user/password styletrack/styletrack by default)
SEED_DEMO=true mvn spring-boot:run
# Swagger UI: http://localhost:8080/swagger-ui.html
```

Tables are created/updated automatically (`DDL_AUTO=update`). For stricter control, switch to
`validate` and add Flyway migrations once your schema settles.

### Configuration (environment variables)
| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL (JDBC URL, e.g. `jdbc:postgresql://host:5432/db`) | localhost dev values |
| `JWT_SECRET` | token signing key, **32+ chars** (required in `prod`) | dev-only value |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | first owner account, created on an empty DB | `admin` / `ChangeMe123!` |
| `SEED_DEMO` | load demo staff, stylists, services, customers | `false` |
| `CORS_ALLOWED_ORIGINS` | comma-separated web front-end origins | localhost 3000/4200/5173 |
| `LOYALTY_PESOS_PER_POINT` | 1 point per this many pesos spent | `100` |
| `LOYALTY_SILVER_VISITS` / `LOYALTY_GOLD_VISITS` | completed visits for Silver / Gold loyal-customer tier | `5` / `10` |
| `QRPH_IMAGE_URL` / `QRPH_ACCOUNT_NAME` | salon QR Ph code image (public URL) and account name shown at payment | empty / `Jonathan Hair Studio` |
| `HOME_SERVICE_FEE` | added to home-service bookings | `0` |
| `TRAVEL_BUFFER_MINUTES` | time reserved around home visits | `30` |
| `SWAGGER_ENABLED` | Swagger UI in `prod` profile | `false` |

Demo logins (only when `SEED_DEMO=true`): `staff1 / Staff@12345`, `stylist1` and `stylist2` / `Stylist@12345`.
PaaS hosts (Render, Railway…) work too: set `SPRING_PROFILES_ACTIVE=prod`, the DB/JWT/admin variables, and `PORT` is honoured.

## Authentication
`POST /api/auth/login` `{ "username": "...", "password": "..." }` → `{ "token": "..." }`
Send `Authorization: Bearer <token>` on every other request. `GET /api/auth/me` returns the current user.

## Role access
| Area | Admin | Staff | Stylist |
|---|---|---|---|
| Users (`/api/users`) | full | – | – |
| Dashboard | salon-wide | salon-wide | own bookings/revenue |
| Appointments: list/view | all | all | own only |
| Appointments: create / reschedule / cancel / payment | ✔ | ✔ | – |
| Accept / reject / complete | ✔ | ✔ | own only |
| Home-service queue (`/api/appointments/home-service`) | ✔ | ✔ | own only |
| Customers + history | full | create/edit/view | view |
| Stylists | full | view | view |
| Stylist schedules | full | view | view + edit own |
| Services | full | view | view |
| Loyalty: balance / redeem points or rewards | ✔ | ✔ | balance only |
| Rewards catalogue (`/api/rewards`) | full | view | – |
| Treatment history (`/api/customers/{id}/treatments`, `/api/treatments`) | full | view | view all; write/edit own |
| Payment monitoring (`/api/reports/payments`) | ✔ | ✔ | – |
| Loyalty: manual adjust | ✔ | – | – |
| Recommendations | full | full | view |
| Reports (`/api/reports/*`) | ✔ | – | – |

## Main endpoints
- `GET/POST /api/appointments`, `PUT /{id}/accept|reject|cancel|complete|reschedule|payment`, `GET /home-service`
- `GET /api/stylists/{id}/availability?date=2026-10-12&serviceId=1&homeService=false` – free start times
- `GET/POST/PUT/DELETE /api/customers`, `GET /api/customers/{id}/history`
- `GET/POST/PUT/DELETE /api/stylists`, `/api/stylists/{id}/schedules`
- `GET/POST/PUT/DELETE /api/services`
- `GET /api/loyalty/customers/{id}`, `/transactions`, `POST /redeem`, `POST /adjust`
- `GET /api/reports/summary|revenue-daily|top-services|stylist-performance|payments?from=&to=`, `GET /api/reports/top-customers`
- `GET /api/appointments?paymentStatus=UNPAID|PAID` – payment monitoring queue; `GET /api/settings/payment` – salon QR Ph details
- `GET /api/customers/{id}/treatments`, `POST/PUT/DELETE /api/treatments` – treatment history
- `GET/POST/PUT/DELETE /api/rewards`, `POST /api/loyalty/customers/{id}/redeem-reward`
- `GET /api/recommendations/customer/{id}`, `/suggestions`, `POST /generate`, plus manual CRUD
- `GET /api/dashboard`

## Customer mobile app API (`/api/me/**`, role `CUSTOMER`)
- `POST /api/auth/register` (public) - creates the account and signs in; the **mobile number is the login name**
  (`+63 917...` and `0917...` are the same number). A number already on file from a walk-in is *not* claimed
  automatically (it is unverified); the front desk calls `POST /api/customers/{id}/app-account` with a starting
  password instead. Customers can change it with `PUT /api/me/password`.
- `GET/PUT /api/me` profile · `GET /api/me/services` · `GET /api/me/stylists` (no contact details) ·
  `GET /api/me/stylists/{id}/availability`
- `GET/POST /api/me/appointments`, `PUT /{id}/cancel|reschedule` - customer bookings are always `PENDING` until
  the salon accepts or rejects them; a customer move also returns to `PENDING`. Customers can only touch their own.
- `GET /api/me/loyalty`, `/loyalty/transactions`, `/recommendations`
- `GET /api/me/notifications`, `/notifications/unread-count`, `PUT /notifications/{id}/seen`, `/notifications/seen-all`
  - created when the salon accepts/rejects/moves/cancels a booking or completes a service.
- Customer accounts are blocked from every back-office endpoint (dashboard, customers, stylists, schedules,
  users, reports...). They are hidden from the Users list.

## Online payments with PayMongo (GCash and QR Ph)

Customers pay a confirmed booking from the mobile app. The backend talks to PayMongo with a **secret key that never
leaves the server**; the app only receives a GCash link or a QR Ph image.

**Set up**
1. In the PayMongo dashboard, make sure **GCash** and **QR Ph** are enabled for your account (PayMongo may need to
   activate them first) and copy your secret key (`sk_test_...` for testing, `sk_live_...` in production).
2. Set `PAYMONGO_SECRET_KEY` and `APP_PUBLIC_URL` (the public https address of this API) in `.env`.
3. In the dashboard create a **webhook** for `https://<your-api>/api/payments/paymongo/webhook` listening to
   `payment.paid` and `payment.failed`. PayMongo shows its signing secret once: put it in `PAYMONGO_WEBHOOK_SECRET`.
   PayMongo must be able to reach the URL, so on a laptop you need a public tunnel; without the webhook the app still
   confirms payments by checking with PayMongo while the payment screen is open.
4. With `PAYMONGO_SECRET_KEY` empty, online payment is simply switched off (the app hides the buttons).

**How it works**
- `POST /api/me/appointments/{id}/pay` `{ "method": "GCASH" | "QRPH" }` creates a PayMongo payment intent, a payment
  method and attaches them. It returns `redirectUrl` (GCash) or `qrImage` (QR Ph). Only `ACCEPTED`/`COMPLETED`
  bookings of the signed-in customer, and not already paid, can be paid.
- `GET /api/me/appointments/{id}/online-payment` returns `NONE | PENDING | PAID | FAILED | EXPIRED | NEEDS_REFUND`
  and, while pending, asks PayMongo for the real state (at most every 3 seconds).
- `POST /api/payments/paymongo/webhook` is public but must carry a valid `Paymongo-Signature` (HMAC-SHA256 of
  `<t>.<raw body>`). The event is never trusted: the payment intent is fetched from PayMongo, the amount is compared
  with what we asked for, and only then is the booking marked `PAID` (method `GCASH`/`QRPH`, reference = PayMongo
  payment id `pay_...`) and the customer notified.
- If money arrives for a booking that was already paid or has since been cancelled, the payment is stored as
  `NEEDS_REFUND` and logged. **Refunds are not automated**: refund it in the PayMongo dashboard.

## Business rules
- **No double booking:** a booking must fit inside one of the stylist's schedule blocks and cannot overlap
  another PENDING/ACCEPTED booking for the same stylist (or the same customer). The stylist row is locked while
  booking, so simultaneous requests cannot take the same slot.
- **Home service:** needs an address, a service flagged home-service-capable, and a schedule block flagged for
  home service. Reserves a travel buffer around the visit. Starts as `PENDING` until accepted/rejected
  (a rejection needs a reason); salon bookings entered by staff start as `ACCEPTED`. Rescheduling a home-service
  booking returns it to `PENDING`.
- **Loyalty:** completing an appointment automatically credits points (the service's fixed `pointsAwarded`, else
  total ÷ `LOYALTY_PESOS_PER_POINT`). Every change is recorded in a ledger with the running balance.
- **Loyal customers:** every completed service adds to the customer's `completedVisits`; reaching
  `LOYALTY_SILVER_VISITS` / `LOYALTY_GOLD_VISITS` makes them SILVER / GOLD. Points can be redeemed freely or
  against a reward from the catalogue.
- **Treatment history:** a stylist (or admin) records what was done, products used, reactions and a suggested
  follow-up service and delay. One record per appointment. Stylists can only write/edit their own.
- **Recommendations:** staff can write manual ones; `generate` builds data-driven ones (stylist follow-ups from
  the treatment history first, then services the customer rebooks, then popular services they haven't tried)
  and replaces the previous automatic set.
- Customers, services, stylists and users are deactivated rather than deleted, so history and reports stay intact.
- **Payments:** methods are `CASH`, `QRPH` and `GCASH` ("QR Ph" is accepted and normalised). QR Ph and GCash payments
  require the wallet's reference number, which must be unique per method; the time of payment is recorded.
  Front desk still records counter payments by hand; customers can also pay from the mobile app through PayMongo
  (see "Online payments" below). `GET /api/reports/payments` shows what was collected per method and what completed services are still unpaid.

## Project layout
`config` (security, OpenAPI, seed) · `security` (JWT) · `entity` · `repository` · `service` · `controller` ·
`dto` · `payment` (PayMongo client, webhook signature) · `exception`. Tests: `BookingFlowIntegrationTest`,
`OnlinePaymentIntegrationTest` (PayMongo mocked), `PayMongoSignatureTest` (H2 in PostgreSQL mode).
