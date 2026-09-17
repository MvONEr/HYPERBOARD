# Session log

Append-only log of Claude Code changes. Newest first. One section per session.

Format:
```
## YYYY-MM-DD HH:MM
- bullet describing what changed
- ...
```

---

## 2026-09-17 10:10
- Replaced the stub root `README.md` with a full project readme: what Hyperboard is and why, the ingest → reconstruct → compute pipeline (including how `PositionReconstructor` infers positions from fills and handles truncated history), repo layout, stack table, local setup (Docker Postgres, `mvn spring-boot:run`, `npm run dev`, seeding via the admin endpoints), full API table split into public / debug / admin, data model, metric definitions, testing posture, a "designed but not built" section pointing at the blueprint and composite-score docs, a docs index, and repo conventions.

## 2026-05-02 14:45
- Created `docs/SESSION_LOG.md` (this file).
- Created root `CLAUDE.md` with the instruction to append to this log after making changes.
- Fixed broken SLF4J log line in `backend/.../stats/StatsComputationService.java` — `{:.1f}%` (Python format) was being printed literally and shifting `winRate*100` into the `pnl={}` slot. Switched to `{}` placeholders with a pre-formatted `String.format("%.1f", ...)`.
- Added `.claude/` to `.gitignore` (local session lock files).
- First real commit of the project: backend, frontend, docs, CLAUDE.md, .gitignore.
- Created `docs/LEADERBOARD_BLUEPRINT.md` — discovery via WS `trades` subscription (only HL traders, no random addresses, no HL leaderboard scrape), pre-screen criteria, hourly active check + daily full recompute, top-100 display, V3 schema additions (`composite_score`, `source` column), suggested package layout (`discovery`, `scoring`, `scheduling`). Composite score formula deferred.
- Updated `LEADERBOARD_BLUEPRINT.md` to fold in candidate-queue admission model: WS harvest only writes `wallet_metadata`, weekly batch evaluates 50 candidates with 30d cooldown, union-and-swap against the pool. Schema gains `in_pool` + `evaluated_at` columns and partial indexes. Sequencing reordered so the weekly batch is what first populates the pool.
- Re-ran the math on whether 50/week is competitive (it isn't — full coverage of an estimated N≈1,000 pre-screen-eligible wallets would take ~20 weeks). API headroom is much bigger than the original conservative number assumed.
- Bumped candidate batch to **150/day with 5d cooldown** (Spring cron `0 30 3 * * *`, runs ~30 min after the daily pool recompute). Full coverage of N=1,000 in ~1 week. ~90 min/week of API time, under 0.3% of HL's per-IP rate limit. Renamed `WeeklyCandidateBatchJob` → `DailyCandidateBatchJob` and updated all references in the blueprint (pipeline diagram, §5 cadences, §7 packages, §8 diff table, §10 sequencing).
- Designed the composite score. Created `docs/COMPOSITE_SCORE.md` with goals, components, percentile-rank normalization rationale, formula (0.25 roe_90d + 0.20 sharpe + 0.20 dd_inv + 0.15 pf + 0.10 trades_30d + 0.10 volume), edge cases, and future tuning notes. Decision: no copy-tradability filter (proportional sizing handles any account size). Updated blueprint §3 (no longer deferred — points at the new doc), §6 (V3 migration adds `trade_count_30d` and `total_volume` columns), §9 (open questions), §10 sequencing (step 6 now points at COMPOSITE_SCORE.md).
