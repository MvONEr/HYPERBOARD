# Composite score — design

The single number that ranks the leaderboard. v1 formula, weights, and
rationale. Implementation lives in `com.hyperboard.scoring.CompositeScorer`
(per `LEADERBOARD_BLUEPRINT.md`).

## Goals

1. **Honesty** — surface what HL hides (max drawdown, true win rate,
   risk-adjusted return).
2. **Activity rewarded** — more trades and volume → higher score
   (engagement + better copy-trading targets).
3. **Penalize hidden risk** — drawdown, blow-up risk.
4. **Don't let one big winning trade dominate** — weight win quality,
   not just P&L.
5. **Account-size agnostic** — copy-trading sizes by % of equity, so any
   account size is followable. No filter on absolute position size.

## Components

All inputs are per-wallet, derived during the daily recompute. Six
components, each percentile-ranked across the comparison set
(pool ∪ batch on a given day).

| Symbol         | Definition                                              | Source                                  |
|----------------|---------------------------------------------------------|-----------------------------------------|
| `roe_90d`      | `pnl_90d / account_value`                               | computed at recompute time              |
| `sharpe`       | per-position ROI mean / stdev (existing)                | `wallet_stats.sharpe`                   |
| `dd_inv`       | `1 - max_drawdown`                                      | derived from `wallet_stats.max_drawdown`|
| `pf`           | `profit_factor` (capped 9999, existing)                 | `wallet_stats.profit_factor`            |
| `trades_30d`   | closed-position count in last 30d                       | aggregate over `positions`              |
| `volume`       | sum of fill notionals (`px * sz`) across all fills      | aggregate over `raw_fills`              |

Win rate is **not** in the score — misleading on its own (90% win rate
with 1% wins and 10% losses is bad). Stays as a display-only stat.

## Normalization — percentile rank

Each component is converted to a 0–1 percentile rank across the current
comparison set. Highest value = 1.0, lowest = 0.0, ties get average rank.

**Why percentile rank, not z-score:** outliers are real in this domain.
A wallet with Sharpe 50 would compress everyone else's z-score into a
narrow band. Percentile rank is scale- and outlier-invariant.

**Reference set:** pool (100) ∪ today's candidate batch (150) = up to
250 wallets per scoring run. The score is meaningful only relative to
this set, which is fine — the score is for ranking, not absolute
quality measurement.

## Formula

```
score = 0.25 * pct_rank(roe_90d)
      + 0.20 * pct_rank(sharpe)
      + 0.20 * pct_rank(dd_inv)
      + 0.15 * pct_rank(pf)
      + 0.10 * pct_rank(trades_30d)
      + 0.10 * pct_rank(volume)
```

Range: [0, 1]. Stored in `wallet_stats.composite_score NUMERIC(12, 4)`.
Displayed × 100 with 1 decimal (e.g. `87.3`) — display formatting only,
storage stays 0–1.

## Why these weights

| Weight | Component   | Reasoning |
|--------|-------------|-----------|
| 0.25   | `roe_90d`   | Primary signal. ROE not raw PnL → fair across $50k and $5M accounts. |
| 0.20   | `sharpe`    | Smooth profits beat lumpy ones. |
| 0.20   | `dd_inv`    | The differentiator vs. HL. A high-PnL wallet that survived a 70% drawdown is not the same kind of trader as one with 15%. |
| 0.15   | `pf`        | Filters lottery winners. One big win produces good PnL but rotten profit factor. |
| 0.10   | `trades_30d`| Rewards live activity, not coasting. Engagement + copy-trade fitness. |
| 0.10   | `volume`    | Trader seriousness. $5M lifetime volume is materially different from $150k. |

Activity + volume = 20% combined. Honesty signals (sharpe + dd_inv +
pf) = 55%. Returns = 25%.

## Edge cases

- **Negative total PnL** — no hard floor. ROE component naturally
  bottom-ranks losing wallets; top-100 cutoff means they don't appear
  on the leaderboard anyway.
- **`account_value = 0`** (closed-out wallet) — `roe_90d = NULL`. Treat
  any null input as bottom percentile (0.0) for that component.
- **`trades_30d = 0`** — gets percentile 0 for that component. Inactive
  wallets are penalized as designed.
- **Single-wallet comparison set** (cold start) — percentile rank
  degenerates (everyone gets 0.5). Acceptable; leaderboard is sparse
  anyway and the formula's relative purpose is moot.
- **Ties** — average rank (standard percentile-rank tie handling).

## Where it runs

- Pure function inside `com.hyperboard.scoring.CompositeScorer`. Takes a
  `List<WalletStats>` (pool ∪ batch on a given day), returns a map of
  `wallet → composite_score`.
- Called from `DailyFullRecomputeJob` (pool wallets) and immediately
  again from `DailyCandidateBatchJob` (pool ∪ batch) so the union-and-
  swap step has fresh, comparable scores.
- Daily run cost: one O(n) percentile-rank per component × 6 components
  × ~250 wallets = trivial.

## What the score depends on (data dependencies)

The score formula introduces two new computations during the daily
recompute that aren't in `wallet_stats` today:

1. **`trade_count_30d`** — count of closed positions where
   `closed_at >= now - 30d`. Cheap aggregation over the existing
   `positions` table.
2. **`total_volume`** — sum of fill notionals (`px × sz`) across all
   `raw_fills`. Cheap aggregation over `raw_fills`. Per-wallet, one
   `SUM` query in the recompute.

Both stored on `wallet_stats` for fast leaderboard rendering — see the
schema delta in `LEADERBOARD_BLUEPRINT.md` §6.

## Future tuning (not for v1)

- **Weight rebalancing.** If users tell us drawdown discipline matters
  more, push `dd_inv` up. Don't tune in v1 without real signal.
- **Per-style sub-leaderboards.** Scalpers and position traders
  shouldn't necessarily share one ranking; their volume and activity
  profiles are too different.
- **All-time ROE alongside 90d.** Adds robustness against short streaks
  at the cost of responsiveness. v1 stays 90d-only.
- **Volume transform.** Currently raw-percentile-ranked, which is
  scale-invariant — fine. If we ever want to weight by absolute
  magnitude (e.g. top 1% of volume gets a real bonus, not just the top
  rank), revisit.
- **Goodhart resilience.** Once the score is public, traders will try
  to game it. Likely first attempt: round-trip micro-trades to inflate
  `trades_30d`. Mitigations exist (require a meaningful per-trade
  notional floor, weight by realized PnL impact, etc.) — defer until
  we see it.
