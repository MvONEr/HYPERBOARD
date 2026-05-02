# Hyperliquid API — Technical Reference

A working reference for building the Hyperliquid trader leaderboard.
Compiled from the official docs and verified community SDKs (April 2026).

---

## Official documentation links

Bookmark these. Re-read before starting each phase of the build.

- API overview: https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api
- Info endpoint: https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint
- Perpetuals info endpoints: https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint/perpetuals
- WebSocket: https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/websocket
- Subscriptions: https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/websocket/subscriptions
- Rate limits: https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/rate-limits-and-user-limits
- Referrals: https://hyperliquid.gitbook.io/hyperliquid-docs/referrals

## Base URLs

- Mainnet REST: `https://api.hyperliquid.xyz`
- Mainnet WebSocket: `wss://api.hyperliquid.xyz/ws`
- Testnet REST: `https://api.hyperliquid-testnet.xyz`
- Testnet WebSocket: `wss://api.hyperliquid-testnet.xyz/ws`

Always test against testnet first. Mainnet behaves identically but real money is involved.

---

## Reference SDKs (read these for endpoint shapes)

You're using Java, but these SDKs are excellent references for the request/response
shapes since there's no official Java SDK.

- Python (official): https://github.com/hyperliquid-dex/hyperliquid-python-sdk
- TypeScript (community, well-maintained): https://github.com/nktkas/hyperliquid
- TypeScript (community): https://github.com/nomeida/hyperliquid
- Rust (community): https://github.com/infinitefield/hypersdk
- Go (community): https://github.com/sonirico/go-hyperliquid

When in doubt about an endpoint shape, grep the Python SDK source — it's the
ground truth.

---

## Info endpoint basics

All info reads go through a single endpoint: `POST /info`.

```
POST https://api.hyperliquid.xyz/info
Content-Type: application/json

{ "type": "<request_type>", ...other_fields }
```

No API key needed for any read endpoints. No authentication for public data.

### Critical pitfall: master vs agent wallet addresses

To query account data for a master account, you MUST pass the actual master
address. Passing an agent (API) wallet address returns an empty result.
Same applies to vaults — you must pass the vault address, not the leader's EOA.

This is a common source of "why is my response empty" bugs.

---

## Endpoints you'll need for the leaderboard

### `userFills` — recent fills for a wallet

Returns at most **2000 most recent fills**. For older history, use
`userFillsByTime` with pagination.

```json
POST /info
{
  "type": "userFills",
  "user": "0xABC...",
  "aggregateByTime": false
}
```

`aggregateByTime`: when true, partial fills from a single crossing order are
combined. For PnL accuracy you usually want this true unless you specifically
need raw partial fills.

Response is an array of fill objects:

```json
{
  "closedPnl": "0.0",         // realized PnL from this fill (string!)
  "coin": "AVAX",             // perp asset name
  "crossed": false,           // true = taker, false = maker
  "dir": "Open Long",         // "Open Long" / "Close Long" / "Open Short" / "Close Short"
  "hash": "0xa166...",        // tx hash
  "oid": 90542681,            // order id
  "px": "18.435",             // fill price (string)
  "side": "B",                // "B" = buy, "A" = sell
  "startPosition": "26.86",   // position size BEFORE this fill (string, signed)
  "sz": "93.53",              // fill size (string)
  "time": 1681222254710,      // ms timestamp
  "fee": "0.01",              // total fee inclusive of builderFee
  "feeToken": "USDC",
  "builderFee": "0.01",       // optional, only present if non-zero
  "tid": 118906512037719      // trade id
}
```

**Coin format quirks:**
- Perp on default dex: plain symbol like `"AVAX"`
- Perp on HIP-3 dex: prefixed like `"xyz:XYZ100"`
- Spot: `"PURR/USDC"` for PURR, otherwise `"@<index>"` like `"@107"` for HYPE

### `userFillsByTime` — paginated historical fills

Returns at most **2000 fills per response**, and only the **10000 most recent
fills are available**. This is a hard limit — you cannot get fills older than
the most recent 10k via the API. Critical for your historical reconstruction.

```json
POST /info
{
  "type": "userFillsByTime",
  "user": "0xABC...",
  "startTime": 1681000000000,   // required, ms
  "endTime": 1681222254710      // optional, defaults to now
}
```

Pagination strategy: query in chunks, walk backwards in time, stop when you
either (a) hit the 10k-fill limit on a wallet or (b) get an empty response.

For wallets older than the 10k-fill window, you will not have full history.
Document this limitation in your stats — show an "earliest data" timestamp.

### `clearinghouseState` — current account state

Returns a wallet's current open positions, margin summary, and account value.
This is your "live" data for the leaderboard — current open positions, current
account value, current liquidation prices.

```json
POST /info
{
  "type": "clearinghouseState",
  "user": "0xABC...",
  "dex": ""                     // optional, "" = default perp dex
}
```

Returns:
- `marginSummary.accountValue` — total equity (string)
- `marginSummary.totalNtlPos` — total notional position size
- `marginSummary.totalMarginUsed` — margin currently in use
- `crossMaintenanceMarginUsed`
- `withdrawable` — what could be withdrawn right now
- `assetPositions[]` — array of currently open positions, each with:
    - `position.coin`
    - `position.szi` — signed size (positive long, negative short)
    - `position.entryPx`
    - `position.positionValue`
    - `position.unrealizedPnl`
    - `position.returnOnEquity`
    - `position.leverage` — leverage settings
    - `position.liquidationPx`
    - `position.cumFunding` — cumulative funding for this position
- `time` — ms timestamp of the snapshot

This single endpoint powers your "live open positions" feature. Poll once per
minute per tracked wallet, or use the `webData2` WebSocket subscription for
real-time updates.

### `userFunding` — funding payments

Funding is separate from fills and must be queried explicitly.

```json
POST /info
{
  "type": "userFunding",
  "user": "0xABC...",
  "startTime": 1681000000000,   // required, ms
  "endTime": 1681222254710      // optional, defaults to now
}
```

Returns funding events with timestamp, asset, amount paid (negative if you
paid funding, positive if you received). Critical for true PnL — without
attributing funding to positions, your PnL math is wrong.

There's also `userNonFundingLedgerUpdates` for deposits/withdrawals/transfers
if you want to compute account-relative returns properly.

### `meta` — list of all perp assets

```json
POST /info
{ "type": "meta" }
```

Returns the universe of tradable perp assets, sizes/lot sizes, max leverage,
etc. Cache this — it changes rarely.

### `spotMeta` — list of all spot assets

```json
POST /info
{ "type": "spotMeta" }
```

Needed if you ever want to display spot history. For a leaderboard focused on
perp traders, you can skip this in v1.

### `allMids` — current mark prices

```json
POST /info
{ "type": "allMids" }
```

Returns a map of coin → current mid price. Cheap to call (weight 2). Cache for
30 seconds.

### `leaderboard` — Hyperliquid's own leaderboard

```json
POST /info
{ "type": "leaderboard" }
```

This is your **seed list of wallets to track**. Pull this on day one to
bootstrap your wallet universe. Combine with WebSocket-discovered wallets over
time to build a more comprehensive set.

### `vaults` — list a user's vaults

```json
POST /info
{
  "type": "vaults",
  "user": "0xABC..."
}
```

Useful for distinguishing personal trading from vault-managed trading. Vaults
should be filtered or labeled separately on your leaderboard.

### `referral` — referral state and earnings

```json
POST /info
{
  "type": "referral",
  "user": "0xABC..."
}
```

Returns who referred this user, plus (if they're a referrer) their earnings
history and their referred users' volume. Useful later for your own affiliate
dashboard.

---

## Rate limits

Per the docs:

- Most info requests: weight 20
- Light requests (`l2Book`, `allMids`, `clearinghouseState`, `orderStatus`,
  `spotClearinghouseState`, `exchangeStatus`): weight 2
- Heavy requests (`userRole`): weight 60
- Paginated endpoints (`userFills`, `userFillsByTime`, `userFunding`,
  `fundingHistory`, etc.) have **additional weight per 20 items returned**
- Maximum of **10 websocket connections** per IP
- Maximum of **1000 websocket subscriptions** per IP

There's an aggregate per-IP limit (the docs note "1200 weight per minute" in
some references). Stay well under it. With proper caching and WebSocket use
for live data, this is rarely a problem.

**Practical implication for your build**: prefer one long-lived WebSocket
connection with many subscriptions over polling REST endpoints. Use REST only
for backfill and periodic full-state refresh.

---

## WebSocket basics

Connect to `wss://api.hyperliquid.xyz/ws` and send subscription messages.

```json
{
  "method": "subscribe",
  "subscription": { "type": "trades", "coin": "SOL" }
}
```

Server responds:
```json
{ "channel": "subscriptionResponse", "data": { ... } }
```

Then you receive a stream of messages on that channel.

**Critical**: you must handle reconnects gracefully. Disconnects happen
periodically without notice. On reconnect, re-subscribe to everything. Most
subscriptions deliver a snapshot tagged `isSnapshot: true` on subscribe — you
can use this to catch up missed data.

### Subscriptions you'll use

- `userFills` — live fills for a specific wallet (your alerts feature)
- `webData2` — comprehensive user state including current positions, orders,
  account value. Single subscription that gives you most of what you need per wallet.
- `clearinghouseState` — live account state for a wallet
- `userEvents` — fills, funding, liquidations, non-user-cancels for a wallet
- `allMids` — live mark prices for everything
- `trades` — live trade feed for a coin (across all users)
- `bbo` — best bid/offer
- `l2Book` — orderbook (probably overkill for your leaderboard)

For each tracked wallet, subscribing to `webData2` is the easiest way to get
near-everything in one feed.

---

## Position reconstruction logic (the hard part)

The API gives you fills, not positions. You need to reconstruct positions
yourself for meaningful stats. Pseudocode:

```
for each (wallet, coin) pair:
    sort fills chronologically by `time`
    current_position = None

    for fill in fills:
        size_signed = fill.sz * (+1 if fill.side == "B" else -1)
        # closing fills have negative effect on existing position size

        if current_position is None:
            # opening a new position
            current_position = new Position(
                entry_price = fill.px,
                size = size_signed,
                opened_at = fill.time,
                cumulative_fee = fill.fee,
            )
        else:
            same_direction = sign(size_signed) == sign(current_position.size)

            if same_direction:
                # adding to position; update weighted avg entry
                new_size = current_position.size + size_signed
                current_position.entry_price = (
                    current_position.entry_price * current_position.size +
                    fill.px * size_signed
                ) / new_size
                current_position.size = new_size
                current_position.cumulative_fee += fill.fee
            else:
                # reducing or flipping
                close_amount = min(abs(size_signed), abs(current_position.size))
                realized_pnl = close_amount * (fill.px - current_position.entry_price) * sign(current_position.size)
                current_position.realized_pnl += realized_pnl
                current_position.cumulative_fee += fill.fee
                current_position.size += size_signed * sign(close_amount)

                if abs(current_position.size) < epsilon:
                    # position fully closed
                    current_position.closed_at = fill.time
                    save_completed_position(current_position)

                    # if there's overflow size, that opens a new flipped position
                    overflow = abs(size_signed) - close_amount
                    if overflow > 0:
                        current_position = new Position(
                            entry_price = fill.px,
                            size = overflow * (-1 if current_position was long else +1),
                            opened_at = fill.time,
                        )
                    else:
                        current_position = None
```

**Edge cases to handle:**
- Position flips in a single fill (long → short directly)
- Hyperliquid's `closedPnl` on each fill — use this as a sanity check against
  your computed realized PnL. They should match within rounding.
- The `startPosition` field on each fill tells you what the position was BEFORE
  the fill. Use this to detect if you've missed fills (e.g., wallet has
  >10k fills and earliest history is unavailable).
- Funding events between fills — attribute funding to the position that was
  open at that timestamp.
- Positions still open at end of fill history — they count as unrealized.

**Validation strategy**: pick 5 wallets including your own and a few from the
public leaderboard. Manually verify your computed PnL against what's shown in
Hyperliquid's UI. If they don't match within 1-2%, find the bug before
moving on.

---

## Stats to compute per wallet

Once you have reconstructed positions:

**Basic:**
- Total realized PnL (USD)
- Total unrealized PnL (open positions only, mark-to-market)
- Total fees paid
- Total funding paid/received
- Net PnL = realized + unrealized + funding − fees

**Performance:**
- Return % (PnL / time-weighted account value, NOT just final equity)
- Win rate = positions with positive net PnL / total closed positions
- Average win, average loss
- Profit factor = gross_wins / gross_losses
- Sharpe-like = mean(per-position-return) / stdev(per-position-return)
- Max drawdown = peak-to-trough on equity curve
- Recovery time after each drawdown

**Behavior:**
- Total trade count
- Average hold duration (close timestamp − open timestamp)
- Trades per active day
- Asset distribution (% PnL by asset, % volume by asset)
- Long vs short distribution
- Average position size relative to account value
- Average leverage used

**Trading style classification (derive from the above):**
- Scalper: avg hold < 1 hour, > 5 trades/day
- Day trader: avg hold < 24h, 1-5 trades/day
- Swing: avg hold 1-7 days
- Position trader: avg hold > 7 days

---

## Data filtering for the leaderboard

To avoid garbage on the leaderboard, filter:

- Account too small (< $1,000 ever traded) — flukes dominate
- Too few trades (< 30 closed positions) — not enough sample size
- Account too new (< 30 days since first trade) — survivorship bias
- Vaults and HLP — list separately, don't mix with individuals
- Wash-trading patterns — same wallet trading against related wallets
  (harder to detect, ignore for v1)

Be transparent on the site about what's filtered.

---

## Architecture for the leaderboard service

**Three logical components:**

1. **Ingestion service (Java, always-on)**
    - WebSocket client subscribed to `webData2` and `userFills` for tracked wallets
    - Periodic REST poller for backfills of newly discovered wallets
    - Writes raw fills/funding/state snapshots to Postgres

2. **Processing service (Java, scheduled job)**
    - Runs every N minutes
    - Reads new raw fills from Postgres
    - Reconstructs positions, computes stats
    - Writes to processed tables: `positions`, `wallet_stats`

3. **API service (Spring Boot REST)**
    - Read-only endpoints for the frontend
    - Heavy caching (Redis or in-memory) — leaderboard query results don't
      need real-time updates beyond every few minutes

**Database tables (rough schema):**

```
raw_fills (wallet, tid, coin, time, side, px, sz, fee, closed_pnl, dir,
           start_position, fetched_at)
raw_funding (wallet, time, coin, amount, fetched_at)
positions (id, wallet, coin, opened_at, closed_at, entry_price, max_size,
           realized_pnl, total_fees, total_funding, hold_duration_ms)
wallet_stats (wallet, computed_at, total_pnl, win_rate, sharpe, max_dd,
              trade_count, avg_hold_ms, trading_style, ...)
wallet_metadata (wallet, first_seen_at, last_active_at, claimed_name,
                 claimed_x_handle, is_vault)
```

---

## Speed/cost tips

- One WebSocket connection can hold hundreds of subscriptions. Don't open
  one connection per wallet.
- Cache `meta` and `spotMeta` for hours — they change rarely.
- Cache `allMids` for 10–30 seconds — fine for leaderboard purposes.
- For the leaderboard top-100 query, recompute every 5 minutes, not on every
  request. Cache the result.
- Postgres `JSONB` columns are fine for storing raw fill objects if you don't
  want to fully normalize.
- For 500 wallets at v1, a single $10/month VPS handles everything.

---

## Common pitfalls (learn from others' mistakes)

1. **Querying with the wrong address** — agent wallet vs master wallet. Always
   use master.
2. **Not handling string number fields** — sizes, prices, PnL are strings to
   preserve precision. Parse them with BigDecimal in Java, not double.
3. **Ignoring funding** — your PnL is wrong if you don't account for funding.
4. **Forgetting the 10k fill history limit** — old wallets won't have full
   history. Show an "earliest data" indicator.
5. **No reconnect logic** — WebSocket disconnects happen. Handle them.
6. **Including HLP / vaults in the leaderboard** — they'll dominate everything
   and skew rankings. Filter explicitly.
7. **Trusting `closedPnl` blindly** — it's mostly correct but verify against
   reconstruction. Edge cases exist around partial fills.
8. **No rate limit handling** — the API will start returning errors if you
   hammer it. Implement exponential backoff.

---

## Referral program (relevant for monetization)

- Need $10,000 cumulative trading volume to create a referral code
- Referrer earns 10% of referred users' fees
- Referred users get 4% fee discount for first $25M of their volume
- Lifetime, no expiration, capped at $1B per referral
- API endpoint to read your referral state: `type: "referral"`

For the leaderboard's monetization, getting your wallet to $10k volume
(trivial) and having a memorable referral code on the site is a real
revenue stream from day one.

---

## Builder codes (for the v2 copy-trading layer, not v1)

Hyperliquid has a separate "builder code" system from referrals. Apps that
execute trades on behalf of users via API wallets can charge a per-trade fee
on top of Hyperliquid's fees. This is how copy-trading platforms monetize.

Don't worry about this for v1 (leaderboard is read-only). Bookmark for v2
when you build the copy-trading execution layer.

Docs: https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/exchange-endpoint
(builder fee approval section)

---

## Java implementation notes

- **HTTP client**: Java 11+ built-in `HttpClient` is fine. Or use OkHttp if
  you prefer.
- **JSON**: Jackson (Spring Boot default). Use `BigDecimal` for all numeric
  fields from the API to avoid precision loss.
- **WebSocket**: Java-WebSocket library
  (https://github.com/TooTallNate/Java-WebSocket) or Spring's WebSocketClient.
  Implement automatic reconnection.
- **Database**: PostgreSQL with JDBC or Spring Data JPA. JSONB columns for
  raw fills are fine.
- **Scheduling**: Spring's `@Scheduled` for the periodic processor.
- **Deployment**: a single Spring Boot fat JAR running on a Hetzner or
  Railway VM. Postgres on the same host for v1, then move to managed when
  you scale.

---

## Starting checklist (week 1–2 build)

- [ ] Open a Hyperliquid account, deposit small amount, do a few test trades
- [ ] Read all linked docs end-to-end, take notes on quirks
- [ ] Set up local Postgres + Java project (Maven/Gradle, Spring Boot)
- [ ] Build minimal HTTP client class with one method: `userFills(address)`
- [ ] Print all fills for your own wallet
- [ ] Validate: count matches what the UI shows
- [ ] Add `userFunding` and `clearinghouseState` calls
- [ ] Add `meta` to know all asset names
- [ ] Implement position reconstruction for one wallet
- [ ] Print computed positions, validate against UI manually
- [ ] Move on to multi-wallet only AFTER one wallet's math is solid