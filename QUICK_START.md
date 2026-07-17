# Quick Start

This guide is for running TradePulse locally on Windows with Docker Desktop, Node.js, and Java.

## 1. Prerequisites

### Required

- Docker Desktop with Compose support
- Node.js 20+
- npm 10+
- Java 21
- Maven wrapper support via the included `mvnw.cmd`

### Recommended

- IntelliJ IDEA for backend services
- a PostgreSQL viewer such as DBeaver
- Postman or Bruno for API checks

## 2. Repository layout

- frontend: `tradepulse-frontend/`
- backend: `tradepulse-backend/`
- backend compose stack: `tradepulse-backend/docker-compose.persistent.yml`
- backend helper scripts: `tradepulse-backend/scripts/`

## 3. Backend environment variables

Create `tradepulse-backend/.env` before starting the backend stack.

Minimum variables used by the compose file:

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`
- `JWT_SECRET`
- `MASSIVE_API_KEY`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `MAIL_SMTP_AUTH`
- `MAIL_SMTP_STARTTLS_ENABLE`
- `MAIL_SMTP_STARTTLS_REQUIRED`
- `MAIL_SMTP_CONNECTION_TIMEOUT`
- `MAIL_SMTP_TIMEOUT`
- `MAIL_SMTP_WRITE_TIMEOUT`

## 4. Start the backend stack

From the repository root, the normal local path is the PowerShell helper:

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend\scripts"
.\up-persistent.ps1
```

Useful alternatives:

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend\scripts"
.\start-persistent.ps1
.\stop-persistent.ps1
```

Direct compose command:

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend"
docker compose --env-file .env -p tradepulse-persistent -f docker-compose.persistent.yml up -d --build
```

## 5. Start the frontend

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-frontend"
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/auth` requests to `http://127.0.0.1:4004`.

## 6. Production-style frontend build

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-frontend"
npm run build
```

## 7. Default ports

- `4004` — API Gateway
- `4005` — Auth Service
- `4000` — Customer Service
- `4001` — Payment Service
- `4003` — Stock Service
- `4006` — Order Service
- `4002` — Analytics Service
- `5000` to `5004` — PostgreSQL containers
- `9002` — Payment gRPC
- `9003` — Stock gRPC
- `9004` — Portfolio sync gRPC
- `9092` / `9094` — Kafka internal / external

## 8. First-run verification checklist

After the stack starts:

1. Open the frontend home page
2. Confirm featured stocks load
3. Confirm market status appears without route-level flicker
4. Register a test user
5. Log in and verify protected features unlock
6. Open wallet and deposit funds
7. Add stocks to cart
8. Lock quote / pay / verify order history
9. Verify portfolio holdings update

## 9. Useful health checks

### Gateway routing

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:4004/api/stocks/featured | Select-Object -ExpandProperty StatusCode
```

### Market status

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:4004/api/stocks/market-status | Select-Object -ExpandProperty Content
```

### Featured cache readiness

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:4004/api/stocks/featured/health | Select-Object -ExpandProperty Content
```

## 10. Common startup problems

### Frontend works but APIs fail

Check that the gateway container is healthy and exposed on `4004`.

### Backend build fails from terminal with Java errors

Confirm `JAVA_HOME` points to a Java 21 installation.

### No market data appears

Check `MASSIVE_API_KEY` and stock-service logs.

### Registration email flow fails

Check mail environment variables and auth-service logs.

## 11. Suggested local workflow

1. Start backend stack
2. Run frontend in Vite dev mode
3. Use browser devtools and service logs together
4. Rebuild only the affected services when changing backend logic
5. Run `npm run build` after frontend changes

