# Hyperboard — Development Decisions

Last updated: April 29, 2026
Project: Hyperliquid trader leaderboard (codename: hyperboard, can rebrand later)
Solo developer, ~5–10 hrs/week, 7-week deadline to ship v1 (June 17, 2026)

This document records the technical and process decisions made for this project,
along with the reasoning behind each. Reread this when tempted to switch stacks.

---

## Guiding principles

1. **Boring, productive choices.** Optimize for shipping, not for learning new tech.
2. **Familiarity beats theoretical superiority.** Java is the right backend because
   the developer knows Java, not because Java is "best."
3. **Use AI heavily for the unfamiliar parts (frontend).** Don't try to learn
   React deeply; assemble using shadcn/ui + Cursor/Claude Code.
4. **Defer every optional decision.** No staging envs, no Kubernetes, no
   custom auth, no microservices. One service, one database, one frontend.
5. **Resist scope creep.** v1 is read-only leaderboard + wallet detail page. That's it.
6. **Validate before scaling.** Build for 500 wallets and 100 daily users. Worry
   about 50,000 users when 50,000 users is a real problem.

---

## Backend stack

### Language: Java 21
- Developer already knows Java
- Modern features (records, pattern matching, virtual threads) are useful
- Use SDKMAN to manage versions: `sdk install java 21-tem`

### Framework: Spring Boot 3.x
- Industry standard, infinite resources
- AI assistants know it well
- Built-in support for everything we need (REST, JDBC, scheduling, Flyway)

### Build tool: Maven
- Boring, stable, well-understood
- Picked over Gradle because Gradle has more sharp edges for solo work

### Database access: Spring Data JDBC (NOT JPA/Hibernate)
- Simpler mental model than JPA
- Faster to debug
- Better fit for our mostly-bulk-insert + read-heavy queries
- If we later need JPA for something specific, we can mix

### HTTP client (for Hyperliquid API): Java 11+ built-in HttpClient
- No external dependency
- Fine for our use case
- Could swap to OkHttp later if we need it

### JSON handling: Jackson (Spring Boot default)
- Always use BigDecimal for fields that come back as strings (sizes, prices, PnL)
- Never use double for financial numbers — precision will bite us

### WebSocket client: Java-WebSocket library
- https://github.com/TooTallNate/Java-WebSocket
- Implement automatic reconnection logic ourselves
- Subscribe to webData2 for tracked wallets (one sub per wallet)

### Schema migrations: Flyway
- SQL files in src/main/resources/db/migration
- Auto-runs on Spring Boot startup
- Easier to reason about than Liquibase

### Logging: SLF4J + Logback (Spring Boot default)
- Print to stdout, Railway captures it
- Add structured fields (wallet address, request id) for easier debugging

### Code formatting: Spotless plugin + Google Java Format
- Auto-runs on build
- Eliminates style debates with myself

### Testing: JUnit 5 + AssertJ + Mockito
- Heavy testing on position reconstruction logic (math correctness matters)
- One or two integration tests against real Hyperliquid API
- Skip exhaustive testing of glue code

---

## Frontend stack

### Framework: Next.js 15 with App Router
- Most popular React framework
- Vercel deployment is trivial
- AI assistants know it deeply
- Server components reduce the amount of client-side React to learn

### Language: TypeScript
- Catches a category of bugs that JavaScript would let through
- Better autocomplete in editor
- Required by most modern React tooling anyway

### Styling: Tailwind CSS
- No custom CSS to maintain
- shadcn/ui is built on it
- AI assistants generate Tailwind classes well

### Component library: shadcn/ui
- Copy-paste components, not a dependency
- Looks professional out of the box
- Fully customizable when needed

### Charts:
- Recharts for general charts (bar, line, pie)
- TradingView Lightweight Charts for equity curves and any candlestick views
- Both are free and look good

### Tables: TanStack Table (if needed) or plain HTML for v1
- Plain HTML tables are fine until we need column sorting + filtering
- Don't pre-optimize

### State management: React state for v1
- No Redux, no Zustand, no Jotai needed
- Server-rendered pages with simple client interactions

### Frontend strategy given my unfamiliarity:
- Lean on shadcn/ui templates as a starting point
- Use Cursor/Claude Code aggressively for component generation
- Copy patterns from existing analytics dashboards (Hyperdash, etc.)
- Don't try to design from scratch — adapt existing layouts

---

## Database

### Postgres 16
- The boring correct choice
- JSON columns available when needed
- Great tooling

### Local: Docker container
```
docker run -d --name hyperboard-pg \
  -e POSTGRES_PASSWORD=dev \
  -e POSTGRES_DB=hyperboard \
  -p 5432:5432 \
  postgres:16
```

### Production: Railway-managed Postgres
- Bundled with the Java service for simplicity
- Migrate to managed Neon or Hetzner-hosted Postgres if costs grow

### Initial schema (sketch — refine as we build):
- `raw_fills` — raw API data, indexed on (wallet, time)
- `raw_funding` — funding payments, indexed on (wallet, time)
- `positions` — reconstructed positions
- `wallet_stats` — computed analytics
- `wallet_metadata` — wallet labels, claimed names, last seen

### Migrations: Flyway, V1__... V2__... naming convention

---

## Hosting and deployment

### Backend: Railway
- ~$10–20/month, includes Postgres
- Deploys from GitHub on push to main
- Zero ops overhead
- Migrate to Hetzner VPS later if needed

### Frontend: Vercel
- Free tier handles all of v1
- Deploys on git push
- Built-in CDN

### Domain: Cloudflare Registrar (or Namecheap as fallback)
- Cheap, no upsells
- Cloudflare also handles DNS + CDN
- Domain TBD — check availability for variations on hyperboard, hypertraders,
  perpboard, etc. .xyz is fine for crypto

### CI/CD: GitHub Actions
- Run tests on PR
- Auto-deploy on merge to main (handled by Railway/Vercel webhooks)

---

## Repo and project layout

### Single repo (monorepo), private GitHub
```
hyperboard/
  backend/        Java/Spring Boot
  frontend/       Next.js
  infra/          deployment configs, scripts
  docs/           HYPERLIQUID_API_REFERENCE.md, this file, architectural notes
  README.md
```

### Branching: main only for solo dev
- Direct commits to main during early development
- Add PR-based workflow later if it ever makes sense

---

## Tooling

### IDE
- IntelliJ IDEA Community Edition for backend (best Java IDE, free)
- Cursor for frontend (AI-first VS Code fork; we want maximum AI help)

### AI assistance
- Cursor in the editor for inline completion and chat
- Claude Code in the terminal for big refactors and explanations
- Treat AI as a pair programmer, especially for frontend

### Database GUI
- DBeaver (free) or TablePlus (paid)

### API testing
- Bruno (open-source) for hitting Hyperliquid API and our own backend

### Version managers
- SDKMAN for Java + Maven
- nvm or fnm for Node

### Package managers
- Maven for Java (no choice)
- npm for Node (default; pnpm is fine but npm avoids questions)

---

## Secrets and config

### Local development
- `.env` file in each service directory, gitignored
- Use Spring Boot's `@Value` to read env vars in Java
- Use Next.js's `process.env.X` for frontend public vars

### Production
- Railway environment variables UI for backend
- Vercel environment variables UI for frontend
- Never commit secrets, ever

### Secrets needed for v1
- DATABASE_URL (Railway provides)
- That's it for the leaderboard. No private keys needed for read-only.

---

## Monitoring and operations

### v1 (minimal)
- Spring Boot Actuator `/health` endpoint
- Better Stack free tier for uptime monitoring
- stdout logs in Railway

### Add when we have users
- Sentry (free tier) for error tracking
- Plausible or Umami for web analytics (NOT Google Analytics)
- Resend for transactional email (when there's email to send)

---

## Testing strategy

### Heavy testing
- Position reconstruction math: many unit tests, edge cases
- Stats computation: unit tests with known inputs

### Light testing
- Integration test that hits real Hyperliquid API for one wallet
- Smoke test that the API endpoints return non-error responses

### Skipped for v1
- E2E browser tests
- Frontend component tests
- Heavy mocking of internal services

---

## Performance targets (v1)

- Track ~500 wallets
- Backfill ~10k fills per active wallet
- Process incoming fills in near real-time via WebSocket
- Recompute leaderboard stats every 5 minutes
- Serve leaderboard page in < 500ms (cached)
- Total infrastructure cost < $40/month

If we exceed these by 2x without more users, that's fine. If we're hitting
limits with no real user growth, something is overbuilt.

---

## Explicitly deferred decisions

These are NOT to be revisited until v1 is shipped and has real users:

- Authentication / user accounts
- Paid tier / Stripe integration
- Email notifications / alerts
- Mobile app
- Copy-trading execution
- Wallet claiming with signed messages
- Advanced filters beyond timeframe + style
- Multi-language support
- Multiple chain support (we are Hyperliquid-focused)
- Custom domain email
- Marketing automation
- Affiliate dashboard
- Custom UI design (use shadcn defaults)

If these come up while building, the answer is "v2."

---

## Non-negotiables

These are decisions that will NOT change without strong cause:

1. Java backend (no rewriting in Rust/Go because something seems faster)
2. Postgres (no NoSQL detour)
3. Single service (no microservices for v1)
4. Read-only v1 (no execution features in first ship)
5. June 17, 2026 ship deadline (move scope, not the date)
6. 5–10 hrs/week (no burning out)

---

## Open questions to resolve early

- [ ] Pick the brand/domain name
- [ ] Open Hyperliquid account and complete $10k volume to enable referral code
- [ ] Choose a name for the public X handle
- [ ] Decide: pseudonymous or real-name founder voice
- [ ] Identify 5–10 real Hyperliquid users for week-1 validation conversations

---

## Reference files

- `HYPERLIQUID_API_REFERENCE.md` — full API technical reference
- `DEVELOPMENT_DECISIONS.md` — this file
- (later) `ARCHITECTURE.md` — service-level architecture
- (later) `STATS_COMPUTATION.md` — exact formulas for leaderboard stats