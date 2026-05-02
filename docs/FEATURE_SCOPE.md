# Hyperboard — v1 and v2 Feature Scope

Last updated: April 29, 2026
Project: Hyperliquid trader leaderboard
v1 ship target: **June 17, 2026** (7 weeks from kickoff, 6 build weeks + 1 buffer)

This document defines exactly what's in and out of scope for v1 and v2.
When in doubt during the build, refer back here. If a feature isn't in v1,
it doesn't get built in v1, no matter how tempting.

---

## Guiding philosophy

**v1 exists to validate one thing:** does a more honest, deeper Hyperliquid
trader leaderboard attract real users and meaningful engagement?

That's it. v1 is not a business yet. It's read-only, ad-supported by
referral links at most, and exists to gather a real audience.

**v2 turns that audience into revenue** through paid features (alerts,
copy-trading, advanced analytics) and platform mechanics (claimable wallets,
caller marketplace).

**v3+ is the multi-source portfolio builder** — out of scope for this doc.

---

## v1 — The leaderboard (ship June 17, 2026)

### What v1 IS

A public, free, read-only website that ranks Hyperliquid traders with depth
and honesty the native leaderboard lacks.

### v1 features

#### Leaderboard page
- Sortable table of top ~500 wallets
- Columns: rank, wallet (truncated address), 30d PnL %, all-time PnL $,
  win rate, max drawdown, average hold duration, trade count
- Filter by timeframe: 30d, 90d, all-time
- Filter by trading style: scalper / day / swing / position trader
- Pagination or "show more"

#### Wallet detail page
- Full wallet stats: PnL, win rate, max drawdown, Sharpe-like ratio,
  profit factor, average win, average loss, average hold time, trade count
- Equity curve chart (account value over time)
- Currently open positions: asset, side, size, entry price, unrealized PnL,
  liquidation distance
- Recent activity timeline: last 20 fills with timestamps
- Asset breakdown: PnL by coin, volume by coin
- Funding earned vs paid
- Trading style classification badge

#### Live activity feed (homepage component)
- Rolling stream of "Trader X just opened/closed Y position"
- Pulls from WebSocket subscriptions to tracked wallets
- Last 50 events, auto-refreshing

#### Search
- Paste any Hyperliquid wallet address, get its detail page
- Indexed for any tracked wallet, on-demand pull for unknown wallets

#### Honest stats
- Max drawdown is shown (the painful number native leaderboard hides)
- Win rate AND average win/loss (so high-win-rate-with-tiny-wins traders
  don't look better than they are)
- Sharpe-like ratio for risk-adjusted comparison
- Time-window selectors that aren't cherry-picked

#### Vault and HLP filtering
- Hyperliquid's HLP and major vaults clearly labeled
- Filter to "individual traders only" by default
- Vaults visible separately if desired

#### Basic site infrastructure
- Responsive layout (works on mobile, doesn't have to be beautiful on mobile)
- Fast page loads (target < 500ms cached)
- Footer with: about, contact (email), GitHub link, X handle
- Hyperliquid referral CTA on key pages: "Sign up to Hyperliquid with code
  HYPERBOARD for 4% off fees" (or whatever code we end up with)

#### Content
- Single "About" page explaining what we filter, why, methodology
- One blog post or About page section explaining "how to read this leaderboard"

### v1 explicitly does NOT include

- ❌ User accounts / login / signup
- ❌ Email or any signup flow
- ❌ Paid tier / Stripe
- ❌ Alerts (no Telegram, email, or in-app)
- ❌ Copy-trading or any trade execution
- ❌ Wallet claiming / verified identities
- ❌ Custom display names
- ❌ Smart money aggregate signals
- ❌ Watchlists (move to v2)
- ❌ Position lifecycle replay (move to v2)
- ❌ Liquidation history page
- ❌ Advanced filters beyond timeframe + style
- ❌ Mobile app
- ❌ API access for users
- ❌ Custom UI design beyond shadcn defaults
- ❌ Multi-language support
- ❌ Multi-chain / non-Hyperliquid data
- ❌ Tax export / CSV downloads
- ❌ Anything requiring authentication

### v1 success criteria (measured 4 weeks after launch)

- 50+ unique daily visitors
- 10+ pieces of qualitative feedback from real Hyperliquid users
- At least 5 wallets shared organically (someone posts a wallet detail page)
- 5+ Hyperliquid signups via referral code
- Decision point: does this deserve v2 investment, or pivot?

### v1 out of scope for the timeline (will not ship even if "easy")

If a feature feels easy to add in week 5, but isn't in the v1 list above,
it does not ship. Scope creep is the leading killer of solo projects.
Write it down for v2 and move on.

---

## v2 — Monetization and engagement (target: months 4–9 post-v1)

### What v2 IS

Turning the audience built by v1 into paying users and a platform with
real network effects.

### v2 features

#### User accounts
- Sign in with wallet (sign a message, no email/password)
- Or simple email signup as fallback for non-crypto-native users
- Free tier and paid tier(s)

#### Watchlists
- Logged-in users can save wallets to a personal watchlist
- Quick-access dashboard showing all watched wallets at a glance
- Free tier: 5 wallets max
- Paid tier: unlimited

#### Alerts
- Telegram bot integration (primary delivery channel for crypto)
- Email as secondary
- Alert types:
    - Trader opens new position
    - Trader closes position
    - Trader's account drops below threshold (potential drawdown)
    - Trader added to your watchlist takes any action
    - Liquidation distance threshold for any watched wallet
- Free tier: 3 alerts max
- Paid tier: unlimited

#### Wallet claiming
- A trader can prove ownership of a wallet by signing a message
- Claimed wallets get:
    - Custom display name on leaderboard
    - Linked X handle
    - "Verified" badge
    - The trader can write a short bio
- Encourages traders to want to be on the leaderboard

#### Position lifecycle replay
- For any closed position, see the full history: opens, adds, partial closes,
  funding accrued, fees, final net PnL
- Visual timeline showing how the position evolved
- Useful for studying how leaders manage positions, not just their entries/exits

#### Liquidation history page
- Per-wallet history of liquidations
- Filter on the leaderboard by "no liquidations in 90d"

#### Smart money aggregate signals
- "8 of the top 50 traders went long SOL in the last 4 hours"
- "Top 10 traders by 90d return are net short BTC"
- Surfaces when consensus forms among profitable wallets
- Free tier: delayed by 1 hour
- Paid tier: real-time

#### Advanced filtering
- "Profitable swing traders, account size $100k–$1M, win rate > 55%,
  max drawdown < 30%, mostly trade ETH and SOL"
- Save filter presets per user
- Discoverability becomes a real product

#### Copy-trade-readiness score
- Composite score blending consistency, drawdown, hold duration, win rate
- Designed to identify traders worth copying vs lottery winners
- Our opinionated metric — branded and explainable

#### Paid tier (pricing TBD, target $20–40/month)
- Unlimited watchlists and alerts
- Real-time smart money signals
- Advanced filtering and saved presets
- Email reports (weekly/daily digest)
- Priority site features as we add them
- Maybe: API access for power users

#### Affiliate / referral revenue (auto-active from v1, optimized in v2)
- Hyperliquid sign-ups via our referral code
- Track and display attribution in the user's dashboard
- Possibly: builder code revenue once we add execution

### v2 explicitly does NOT include

- ❌ Copy-trading execution (move to v3)
- ❌ Multi-source portfolio builder (v3)
- ❌ Multi-exchange support (Binance/Bybit/etc.)
- ❌ Native mobile app
- ❌ Caller marketplace where traders can charge subscriptions

### v2 success criteria

- 1,000+ registered users
- 100+ paying users at $20–30/month = $2k–3k MRR
- $1k+/month in Hyperliquid referral commissions
- Sustained organic growth (new users from word-of-mouth, not paid)

---

## v3+ — Execution layer and portfolio builder (out of detailed scope here)

### Conceptual scope

#### Copy-trading
- User connects via Hyperliquid API wallet (delegated, can trade not withdraw)
- Auto-mirror selected leader wallets with risk controls (max position size,
  stop loss, leverage cap)
- Charge a fee per trade (builder code) or % of profits

#### Caller marketplace
- Verified callers can offer paid subscriptions through the platform
- We take a platform cut
- Callers must have published track records on the leaderboard
- Trust mechanism for callers + monetization for talent

#### Multi-source portfolio builder
- User allocates capital across multiple alpha sources
- Per-source position sizing, risk caps, stop losses
- Portfolio-level view across all sources

### v3 will likely require
- Co-founder or first hire (custodial-adjacent execution is too much for solo)
- Real legal review (jurisdiction strategy, T&Cs)
- Additional capital (infra, security, ops)
- Probably 12+ months after v2 launches

---

## Scope discipline rules

These rules prevent the most common solo-founder failure modes.

### Rule 1: "v2" is the answer to most "should we add..." questions during v1 build

Write the idea in `IDEAS_FOR_LATER.md` and move on. Don't add it.

### Rule 2: No feature in v1 requires server-side state for users

If a feature needs login, accounts, or per-user data, it's v2 by definition.
v1 is fully public, fully read-only.

### Rule 3: Any execution / write feature is v3 minimum

Touching user funds or making trades on their behalf changes everything:
security stakes, legal stakes, support load. Not in v1 or v2.

### Rule 4: If unsure, defer

Better to ship a small, working v1 on June 17 than a sprawling v1.5 in
August that nobody sees because we never finished.

### Rule 5: Listen to v1 users before designing v2

Build the v2 features above, but stay open to changing them based on what
v1 users actually ask for. The list in this doc is our best current guess,
not a fixed plan.

---

## Cross-reference

- `HYPERLIQUID_API_REFERENCE.md` — technical API reference for the build
- `DEVELOPMENT_DECISIONS.md` — stack, tooling, process decisions
- `FEATURE_SCOPE.md` — this file