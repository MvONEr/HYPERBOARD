# Hyperboard

A Hyperliquid trader leaderboard with the numbers the native one leaves out.

Hyperliquid's own leaderboard ranks traders by PnL over cherry-picked windows.
That flatters lucky traders and hides the risk they took to get there.
Hyperboard reconstructs each wallet's positions from raw fills and reports max
drawdown, profit factor, average win vs. average loss, a Sharpe-like ratio, and
hold-duration-based style classification alongside PnL.


---

## How it works

Hyperboard doesn't trust Hyperliquid's aggregate stats. It pulls **raw fills**
per wallet and derives everything itself:

```
Hyperliquid /info API
        │
        │  userFills, userFunding, clearinghouseState
        ▼
   raw_fills, raw_funding            ← WalletIngestionService
        │
        │  group by coin, walk fills chronologically,
        │  track weighted-average entry, detect flips
        ▼
   positions (open + closed)         ← PositionReconstructor
        │
        │  PnL windows, win rate, profit factor, Sharpe,
        │  max drawdown, avg hold, style classification
        ▼
   wallet_stats                      ← StatsComputer
        │
        ▼
   GET /api/leaderboard  ──→  Next.js frontend
   GET /api/wallet/{addr}/summary
```

Position reconstruction is the load-bearing piece and the one place worth
reading closely
([`PositionReconstructor.java`](backend/src/main/java/com/hyperboard/domain/PositionReconstructor.java)).
Hyperliquid returns fills, not positions, so a position is inferred by walking a
coin's fills in time order: same-direction fills update the weighted-average
entry price, opposite-direction fills close size, and a fill that crosses
through zero closes one position and opens another. It also handles the
truncated-history case — when the oldest fill has a non-zero `startPosition`,
there was already an open position before the data window, so entry price is
back-inferred from `closedPnl`.

All money values are `BigDecimal` end to end. Never `double` — see
[`docs/DEVELOPMENT_DECISIONS.md`](docs/DEVELOPMENT_DECISIONS.md).

---

## Repo layout

```
backend/     Java 21 + Spring Boot 3.3 — ingestion, reconstruction, stats, REST API
frontend/    Next.js 16 (App Router) + TypeScript + Tailwind + shadcn/ui
docs/        Design docs, API reference, scope, session log
```

---

## Stack

| Layer      | Choice                                                         |
|------------|----------------------------------------------------------------|
| Backend    | Java 21, Spring Boot 3.3.5, Maven                              |
| Data       | Postgres 16, Spring Data JDBC (not JPA), Flyway migrations     |
| HTTP       | JDK built-in `HttpClient` against `api.hyperliquid.xyz/info`   |
| Formatting | Spotless + Google Java Format, applied on `compile`            |
| Frontend   | Next.js 16 App Router, React 19, TypeScript, Tailwind v4       |
| UI         | shadcn/ui components (`frontend/src/components/ui`)            |
| Hosting    | Railway (backend + Postgres), Vercel (frontend) — not yet live |

---

## Running it locally

### 1. Postgres

```bash
docker run -d --name hyperboard-pg \
  -e POSTGRES_PASSWORD=dev \
  -e POSTGRES_DB=hyperboard \
  -p 5432:5432 \
  postgres:16
```

Flyway runs the migrations in `backend/src/main/resources/db/migration` on
startup, so there's nothing to apply by hand.

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

Serves on `http://localhost:8080`. Health check at `/actuator/health`.

Config is env-var driven with local-dev defaults baked in
([`application.yml`](backend/src/main/resources/application.yml)):

| Variable            | Default                                        |
|---------------------|------------------------------------------------|
| `DATABASE_URL`      | `jdbc:postgresql://localhost:5432/hyperboard`  |
| `DATABASE_USER`     | `postgres`                                     |
| `DATABASE_PASSWORD` | `dev`                                          |

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Serves on `http://localhost:3000` and redirects `/` → `/leaderboard`. Point it
at a non-local backend with `NEXT_PUBLIC_API_URL`.

### 4. Get data in

The database starts empty, so the leaderboard will be blank. Seed it:

```bash
# Preview which wallets pass the quality filters (no writes)
curl 'localhost:8080/api/admin/qualifying-wallets?limit=50'

# Pull Hyperliquid's leaderboard, filter, ingest + compute stats
curl -X POST 'localhost:8080/api/admin/seed?limit=30'

# Or seed specific wallets
curl -X POST localhost:8080/api/admin/seed-wallets \
  -H 'Content-Type: application/json' \
  -d '["0xabc...","0xdef..."]'
```

Seeding is deliberately slow — a fixed 2 s delay between wallets keeps us well
inside Hyperliquid's rate limits. 30 wallets takes a couple of minutes.

---

## API

### Public

| Method | Path                              | Notes                                                                  |
|--------|-----------------------------------|------------------------------------------------------------------------|
| `GET`  | `/api/leaderboard`                | `?sort=`, `?style=`, `?limit=` (max 500). Floors at ≥30 closed trades.  |
| `GET`  | `/api/wallet/{address}/summary`   | Everything the wallet page needs, in one call                          |

`sort` accepts `total_pnl` (default), `pnl_30d`, `pnl_90d`, `win_rate`,
`sharpe`, `profit_factor`, `max_drawdown`, `trade_count`. Values are whitelisted
in `LeaderboardDao` rather than interpolated, and `max_drawdown` sorts ascending
because lower is better. `style` filters to `scalper` / `day` / `swing` /
`position`.

### Passthrough + pipeline (dev/debug)

| Method | Path                              | Notes                                          |
|--------|-----------------------------------|------------------------------------------------|
| `GET`  | `/api/wallet/{address}/fills`     | Live from Hyperliquid, unstored                |
| `GET`  | `/api/wallet/{address}/funding`   | `?startTime=` ms epoch                         |
| `GET`  | `/api/wallet/{address}/state`     | `clearinghouseState` passthrough               |
| `GET`  | `/api/wallet/{address}/positions` | Reconstruct live, without persisting           |
| `POST` | `/api/wallet/{address}/ingest`    | Backfill fills + funding into Postgres         |
| `POST` | `/api/wallet/{address}/process`   | Reconstruct positions from stored fills        |
| `POST` | `/api/wallet/{address}/stats`     | Recompute and upsert `wallet_stats`            |

The three `POST`s are the pipeline stages split apart so each can be run and
inspected on its own. `/stats` re-runs reconstruction internally, so it's the
only one you need after an ingest.

### Admin

`GET /api/admin/qualifying-wallets`, `POST /api/admin/seed`,
`POST /api/admin/seed-wallets`. **Unauthenticated** — fine for local work,
must be gated before this is exposed publicly.

---

## Data model

Four tables ([`V1__init.sql`](backend/src/main/resources/db/migration/V1__init.sql)):

- **`raw_fills`** — one row per fill, keyed `(tid, wallet)`. The source of truth.
- **`raw_funding`** — funding payments, keyed `(wallet, time, coin)`. Negative = paid.
- **`positions`** — reconstructed positions; `closed_at IS NULL` means still open.
- **`wallet_stats`** — one row per wallet, the leaderboard's read model.
- **`wallet_metadata`** — registry, vault/HLP flags, future claimed-name fields.

Ingestion and reconstruction are both idempotent: fills upsert on their primary
key, and `PositionDao.replaceForWallet` wipes and rewrites a wallet's positions.
Re-running the pipeline is always safe.

---

## Metrics

| Stat            | Definition                                                                         |
|-----------------|-------------------------------------------------------------------------------------|
| `win_rate`      | Closed positions with positive realized PnL ÷ total closed                          |
| `profit_factor` | Gross wins ÷ gross losses, capped at 9999                                           |
| `sharpe`        | Mean per-position ROI ÷ stdev of per-position ROI (not annualized)                  |
| `max_drawdown`  | Largest peak-to-trough decline on the cumulative realized-PnL curve, clamped to 100% |
| `trading_style` | From avg hold + trades/day: `scalper` <1 h & >5/day, `day` <24 h, `swing` <7 d, else `position` |
| `long_pct`      | Share of closed positions opened long                                               |

Win rate is shown but never ranked on alone — a 90% win rate with 1% wins and
10% losses is a bad trader, which is exactly the illusion Hyperboard exists to
break.

---

## Tests

```bash
cd backend && mvn test
```

Coverage is intentionally lopsided: heavy unit tests on the math that has to be
right (`PositionReconstructorTest`, `StatsComputerTest`), nothing on glue code.
There are no frontend tests, by decision.

---

## What's designed but not built yet

The current pipeline is manually triggered and seeds off Hyperliquid's own
leaderboard. The v1 design replaces both.
[`docs/LEADERBOARD_BLUEPRINT.md`](docs/LEADERBOARD_BLUEPRINT.md) specifies:

- **Discovery via WebSocket** — subscribe to `trades` for the top 5 coins by
  volume (~90% of notional), harvest wallet addresses off every message. Only
  real Hyperliquid traders, no scraping their leaderboard.
- **Candidate queue → pool admission** — a daily batch fully evaluates 1,000
  queued wallets, then unions them against the current pool and keeps the top
  100. Wallets carry a 5-day re-evaluation cooldown.
- **Three refresh cadences** — hourly active check (pool only), daily full
  recompute (pool only), daily candidate batch.
- **Composite score** — the single ranking number, fully specified in
  [`docs/COMPOSITE_SCORE.md`](docs/COMPOSITE_SCORE.md): a weighted blend of
  percentile-ranked 90d ROE, Sharpe, inverse drawdown, profit factor, 30d trade
  count, and volume. Percentile rank rather than z-score, because outliers in
  this domain are real and would otherwise compress everyone else together.

The blueprint's §10 has the build order, sequenced so the pipeline can ship and
run end-to-end on `total_pnl` before the composite score lands.

---

## Docs

| File                                                             | What's in it                                            |
|------------------------------------------------------------------|----------------------------------------------------------|
| [`FEATURE_SCOPE.md`](docs/FEATURE_SCOPE.md)                       | What ships in v1, what doesn't, and the scope-discipline rules |
| [`DEVELOPMENT_DECISIONS.md`](docs/DEVELOPMENT_DECISIONS.md)       | Stack choices with reasoning; reread before switching anything |
| [`HYPERLIQUID_API_REFERENCE.md`](docs/HYPERLIQUID_API_REFERENCE.md) | Endpoints, payload shapes, rate limits                 |
| [`LEADERBOARD_BLUEPRINT.md`](docs/LEADERBOARD_BLUEPRINT.md)       | Discovery, admission, and refresh pipeline design        |
| [`COMPOSITE_SCORE.md`](docs/COMPOSITE_SCORE.md)                   | Ranking formula, weights, edge cases                     |
| [`SESSION_LOG.md`](docs/SESSION_LOG.md)                           | Running log of changes                                   |

---

## Conventions

- `main` only. Direct commits — solo project.
- Spotless reformats Java on every `compile`; don't hand-format.
- `BigDecimal` for anything that represents money or size. No exceptions.
- After a session that changes code or docs, append to `docs/SESSION_LOG.md`
  (see [`CLAUDE.md`](CLAUDE.md)).
