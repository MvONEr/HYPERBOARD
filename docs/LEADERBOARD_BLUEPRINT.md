# Leaderboard blueprint

Technical design for the v1 leaderboard. Consult this when coding the
discovery, scoring, and refresh pipeline. Open product questions are
listed at the bottom.

Owner decisions baked in:
- Source: only wallets that trade on Hyperliquid (no random addresses,
  no HL leaderboard scrape).
- Cold start of weeks is acceptable — quality grows organically.
- Refresh: hourly active check + daily full recompute on the leaderboard
  pool, daily candidate batch.
- Display size: 100 wallets initially.
- Candidate batch size: 1,000 / day. Re-evaluation cooldown: 5 days.
  (Sized against measured N≈7,504 pre-screen survivors — see §11.)
- All cadences and sizes are tunable later — no need to relitigate now.
- Composite score formula: deferred (separate design pass).

---

## Pipeline

```
[HL WebSocket trades]
         │
         ▼
   Wallet harvester ──→ wallet_metadata
                         (in_pool=false, evaluated_at=NULL)
                                │
       ┌────────────────────────┴────────────────────────┐
       │                                                 │
       ▼ daily candidate batch (1,000)                   │
   Full ingest + reconstruct + stats + score             │
   Pre-screen (≥30 trades, ≥$100k vol, etc.)             │
       │                                                 │
       ▼                                                 │
   Union of (current pool ∪ batch) → top 100             │
   Set in_pool flags accordingly (swap)                  │
       │                                                 │
       ▼                                                 │
   wallet_stats (composite_score)                        │
       │                                                 │
       ▼                                                 │
   ─────────────  the 100 leaderboard wallets  ──────────┤
       │                                                 │
       ▼ daily full recompute                            │
   Re-pull fills, reprocess, recompute stats + score     │
       │                                                 │
       ▼ hourly active check                             │
   clearinghouseState → update last_active_at,           │
                       account_value                     │
       │                                                 │
       ▼                                                 │
   GET /api/leaderboard → top 100 by composite_score DESC ◀┘
```

Three cadences, each with a different cost profile — see §5.

---

## 1. Discovery — harvest wallets from the WS trade feed

Source: `wss://api.hyperliquid.xyz/ws`, `trades` subscription per coin.

- Single long-lived connection. Subscribe to the top 5 coins by 24h
  volume (measured: BTC, ETH, HYPE, ZEC, SOL — captures ~89.8% of
  notional volume and ~89.7% of trade count). Pull the list from
  `metaAndAssetCtxs` at startup; refresh weekly.
- Each `trades` message contains a `users` array (buyer + seller wallet
  addresses). On every message, upsert both addresses into
  `wallet_metadata` with `first_seen_at = now` (no-op if already there).
- Reconnect on disconnect; on reconnect, re-subscribe to all coins.
- Discard the trade payload after harvesting addresses — we don't need
  the trade itself, just the wallets.

Why not other discovery options:
- **Random Ethereum addresses** — 99.999% are not HL traders. Useless.
- **HL `leaderboard` endpoint** — excluded by product decision.
- **Block scanning** — heavier infra, no upside over WS.
- **`recentTrades` REST endpoint** — fine as a startup top-up before the
  WS connection has caught up; not the primary mechanism.

Constraints to respect (from `HYPERLIQUID_API_REFERENCE.md`):
- Max 10 WS connections per IP, max 1000 subscriptions. We use 1 + 10. Fine.
- WS disconnects are normal. Reconnect logic is non-negotiable.

---

## 2. Pre-screen — gate before scoring

A wallet enters the leaderboard scoring pass only if it passes ALL of:

| Criterion          | Threshold      | Source                                   |
|--------------------|----------------|------------------------------------------|
| Closed trade count | ≥ 30           | reconstructed positions                  |
| All-time volume    | ≥ $100,000     | sum of fill notional (`px × sz`)         |
| Account value      | ≥ $20,000      | `clearinghouseState.marginSummary`       |
| Recent activity    | fill in 30d    | `MAX(fills.time)` per wallet             |
| Not a vault / HLP  | true           | `wallet_metadata.is_vault`, `is_hlp`     |

Failing wallets stay in `wallet_metadata` (we keep seeing them in the WS
feed) but are excluded from `wallet_stats` writes. Re-evaluated on the
daily full recompute — a wallet that crosses 30 trades tomorrow gets in
tomorrow.

> Note: the existing `WalletSeedingService` filters by account value and
> volume too, but at the wrong stage (before any fills are pulled, off
> HL's `accountValue` and `vlm` fields). The new pipeline applies the
> filter after we have our own data — more reliable, but means we pull
> fills for some wallets that fail screening. Acceptable.

---

## 3. Composite score

Full design in **`docs/COMPOSITE_SCORE.md`**. Summary:

```
score = 0.25 * pct_rank(roe_90d)        # pnl_90d / account_value
      + 0.20 * pct_rank(sharpe)
      + 0.20 * pct_rank(1 - max_drawdown)
      + 0.15 * pct_rank(profit_factor)
      + 0.10 * pct_rank(trades_30d)
      + 0.10 * pct_rank(total_volume)
```

- Percentile rank (not z-score) — robust to outliers.
- Reference set per scoring run: pool ∪ today's batch (≤250 wallets).
- Stored in `wallet_stats.composite_score`, range [0, 1].
- Pure function in `com.hyperboard.scoring.CompositeScorer`. Called from
  both `DailyFullRecomputeJob` and `DailyCandidateBatchJob`.

Two new aggregations land on `wallet_stats` to feed the formula —
`trade_count_30d` and `total_volume`. See §6.

Until the score code lands, the union-and-swap step uses `total_pnl` as
the comparator so the rest of the pipeline can ship without it.

---

## 4. Display

`GET /api/leaderboard` ranks by `composite_score DESC NULLS LAST`,
returns the top 100. The existing alternate sort options (PnL, Sharpe,
etc.) stay — composite is just the new default.

`LeaderboardDao.ALLOWED_SORT` adds `composite_score`. The
`trade_count >= 30` floor in the SQL becomes redundant once pre-screen
gates writes, but keep it as a safety net.

---

## 5. Refresh — three cadences

### Hourly active check (cheap, pool only)
- For every wallet with `in_pool = true`, call `clearinghouseState`
  (weight 2).
- Update `wallet_metadata.last_active_at` if the live state suggests
  activity (open positions changed, account value moved meaningfully).
- Update `wallet_stats.account_value`.
- Cost: ~100 wallets × 1 light request = trivial.
- Spring `@Scheduled(cron = "0 0 * * * *")`.

### Daily full recompute (heavy, pool only)
- For every wallet with `in_pool = true`: re-pull `userFills` +
  `userFunding`, reprocess positions, recompute stats, recompute
  composite score.
- Apply pre-screen *after* stats are recomputed; wallets that fail get
  `composite_score = NULL` (drops them off the leaderboard until they
  qualify again — they stay in `in_pool` for one more day, then can be
  displaced by the next weekly batch).
- Honor the existing 2 s rate-limit delay between wallets — same
  pattern as `WalletSeedingService.seedWallets`.
- ~100 wallets × ~5 s = ~8 minutes. Run off-peak (e.g. 03:00 UTC).
- Spring `@Scheduled(cron = "0 0 3 * * *")`.

### Daily candidate batch (the queue→pool admission gate)

This is how new wallets enter the leaderboard. **No real-time admission**
— scoring requires the expensive full ingest, so we batch.

- Pick 1,000 candidate wallets where `in_pool = false` and
  (`evaluated_at IS NULL` OR `evaluated_at < now - 5d`).
- Selection order: `evaluated_at NULLS FIRST, first_seen_at ASC` —
  wallets we've never evaluated come first; among those, oldest-discovered
  first. Fair queue, no random.
- For each candidate: full ingest + reconstruct + stats + score, with
  the same 2 s delay. Set `wallet_metadata.evaluated_at = now` regardless
  of outcome.
- After the batch completes, **swap**:
  1. Take the union of (current pool ∪ this batch) — at most 1,100 wallets.
  2. Sort by `composite_score DESC NULLS LAST`.
  3. Set `in_pool = true` for the top 100; `in_pool = false` for the rest.
  - Wallets demoted out of the pool keep their `wallet_stats` row and
    their score; they sit in the candidate queue under the 5 d cooldown
    until they're re-evaluated and possibly re-promoted.
- Run right after the daily pool recompute so all scores in the union
  are current. Spring `@Scheduled(cron = "0 30 3 * * *")`
  (~30 min after the 03:00 daily recompute).
- Cost: 1,000 wallets × ~5 s = ~83 minutes per day. ~10 hours/week of
  API time. Per-IP rate is ~38 weight/min averaged — under 3.2% of HL's
  1,200 weight/min ceiling.

For the measured N ≈ 7,504 pre-screen survivors (§11), this cycles
through every non-pool wallet in ~7.5 days.

### Inactivity TTL
- Drop a wallet from the pool if `last_active_at < now - 60d`. Removes
  noise. If the wallet shows up in WS again, we re-add it on first
  trade (a new `wallet_metadata` row with `evaluated_at = NULL`, so it
  jumps to the front of the candidate queue).

### Total weekly cost
- Daily recompute: 100 × 7    = 700    full evaluations
- Daily batch:    1,000 × 7   = 7,000  full evaluations
- Hourly check:    100 × 168  = 16,800 light requests
- **Total: ~7,700 full evals + 16.8k light requests per week** —
  roughly 10 hours of API time, ~38 weight/min averaged. We use under
  3.2% of HL's per-IP limit; room remains to raise rates later.

---

## 6. Schema additions (V3 migration)

```sql
-- V3__leaderboard_pipeline.sql
ALTER TABLE wallet_stats
    ADD COLUMN composite_score NUMERIC(12, 4),
    ADD COLUMN trade_count_30d INT,
    ADD COLUMN total_volume    NUMERIC(30, 10);

CREATE INDEX wallet_stats_composite_score
    ON wallet_stats (composite_score DESC NULLS LAST);

ALTER TABLE wallet_metadata
    ADD COLUMN source         TEXT    NOT NULL DEFAULT 'ws_trades',
    ADD COLUMN in_pool        BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN evaluated_at   BIGINT;
-- source:        'ws_trades' | 'manual' | 'recent_trades_rest'
-- in_pool:       true = on the leaderboard (refresh targets it)
-- evaluated_at:  ms epoch of last full eval, NULL if never evaluated;
--                drives the 5d candidate cooldown.

-- Fast lookup for the daily/hourly jobs (the 100 pool wallets)
CREATE INDEX wallet_metadata_in_pool
    ON wallet_metadata (in_pool) WHERE in_pool = true;

-- Fast lookup for the daily candidate batch (cooldown-eligible wallets)
CREATE INDEX wallet_metadata_candidate_queue
    ON wallet_metadata (evaluated_at NULLS FIRST, first_seen_at)
    WHERE in_pool = false;
```

No new tables in v1 — `wallet_metadata` carries everything discovery and
admission need (`first_seen_at`, `last_active_at`, `in_pool`,
`evaluated_at`, `is_vault`, `is_hlp`).

---

## 7. Backend package layout

New packages:

- `com.hyperboard.discovery`
  - `TradesWebSocketClient` — connects, subscribes, dispatches messages.
  - `WalletHarvester` — consumes WS messages, upserts into
    `wallet_metadata`. Idempotent.
  - `TopCoinsResolver` — picks which coins to subscribe to (top N by 24h
    volume, refreshed weekly).
- `com.hyperboard.scoring`
  - `CompositeScorer` — pure function (`WalletStats[]` → score per
    wallet). Pool-aware so it can normalize.
- `com.hyperboard.scheduling`
  - `HourlyActiveCheckJob` — pool only.
  - `DailyFullRecomputeJob` — pool only.
  - `DailyCandidateBatchJob` — picks 1,000 from the queue, evaluates
    them, runs the union-and-swap step that updates `in_pool` flags.
  - All annotated `@Scheduled`. Skip running if previous run still in
    flight (use a `ReentrantLock` or DB advisory lock).

Existing packages:
- `ingestion` — `WalletSeedingService.seedFromLeaderboard` deprecated;
  remove from `AdminController`. Keep `seedWallets(List<String>)` for
  manual overrides.
- `stats`, `domain`, `api`, `hyperliquid` — unchanged.

---

## 8. What changes vs current code

| Today                                                  | After                                                                    |
|--------------------------------------------------------|--------------------------------------------------------------------------|
| `WalletSeedingService.seedFromLeaderboard` pulls HL    | Removed. Discovery is WS-driven.                                         |
| Manual `POST /api/admin/seed` to populate              | Pool fills automatically once WS is running and the weekly batch runs.   |
| `LeaderboardDao` sorts on raw stat columns             | Default sort is `composite_score`; raw columns remain as options.        |
| Stats only recomputed on manual endpoint               | Daily scheduled recompute for pool wallets; daily candidate batch.       |
| No notion of "active wallet"                           | `last_active_at` updated hourly; wallets time out at 60d.                |
| `wallet_stats.account_value` updated on full recompute | Updated hourly via the active check.                                     |
| Whole `wallet_stats` table = the "leaderboard"         | `wallet_metadata.in_pool` flags the 100; demoted wallets keep stats rows.|
| New wallets entered via manual seed                    | Candidates queue up automatically; daily batch admits up to 1,000/day.   |

---

## 9. Open product/design questions (defer)

- Per-style or per-window sub-leaderboards.
- Whether to expose the score breakdown on the wallet page.
- How to flag/label vaults and HLP automatically (right now `is_vault`
  and `is_hlp` are columns with no population logic).
- Pool ceiling — start at "everyone who passes pre-screen", revisit if
  it grows past a few thousand.
- Composite score weight tuning, all-time ROE component, Goodhart
  hardening — see `COMPOSITE_SCORE.md` "Future tuning".

---

## 10. Sequencing (suggested build order)

1. V3 migration (`composite_score`, `in_pool`, `evaluated_at`, `source`,
   indexes).
2. WS client + harvester writing to `wallet_metadata`. Verify the
   candidate queue grows over a few hours.
3. Daily full-recompute job (using existing ingestion + stats services).
   No-op until something is `in_pool = true`.
4. Hourly active-check job.
5. Daily candidate batch job — picks 1,000 from queue, evaluates, runs
   the union-and-swap step. This is what first populates `in_pool`.
6. `CompositeScorer` implementation per `COMPOSITE_SCORE.md`. Add
   `trade_count_30d` and `total_volume` aggregations to the recompute
   path. Until this lands, the union-and-swap step uses `total_pnl` as
   the comparator.
7. `LeaderboardDao` updated to default-sort on `composite_score`.
8. Remove `seedFromLeaderboard` from `AdminController`.

Steps 1–5 are deployable without 6; the system runs end-to-end on
`total_pnl` until the composite score lands.
