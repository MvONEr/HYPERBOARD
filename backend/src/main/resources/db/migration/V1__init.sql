-- Raw fills from the Hyperliquid API (one row per fill)
CREATE TABLE raw_fills (
    tid             BIGINT          NOT NULL,
    wallet          TEXT            NOT NULL,
    coin            TEXT            NOT NULL,
    time            BIGINT          NOT NULL,  -- ms epoch
    side            CHAR(1)         NOT NULL,  -- 'B' or 'A'
    px              NUMERIC(30, 10) NOT NULL,
    sz              NUMERIC(30, 10) NOT NULL,
    fee             NUMERIC(30, 10) NOT NULL,
    closed_pnl      NUMERIC(30, 10) NOT NULL,
    dir             TEXT            NOT NULL,  -- "Open Long" / "Close Long" etc.
    start_position  NUMERIC(30, 10) NOT NULL,
    crossed         BOOLEAN         NOT NULL,
    oid             BIGINT,
    builder_fee     NUMERIC(30, 10),
    fee_token       TEXT,
    fetched_at      BIGINT          NOT NULL,  -- ms epoch when we stored it
    PRIMARY KEY (tid, wallet)
);

CREATE INDEX raw_fills_wallet_time ON raw_fills (wallet, time);

-- Raw funding payments
CREATE TABLE raw_funding (
    wallet      TEXT            NOT NULL,
    time        BIGINT          NOT NULL,  -- ms epoch
    coin        TEXT            NOT NULL,
    amount      NUMERIC(30, 10) NOT NULL,  -- negative = paid, positive = received
    fetched_at  BIGINT          NOT NULL,
    PRIMARY KEY (wallet, time, coin)
);

CREATE INDEX raw_funding_wallet_time ON raw_funding (wallet, time);

-- Reconstructed closed positions
CREATE TABLE positions (
    id                  BIGSERIAL       PRIMARY KEY,
    wallet              TEXT            NOT NULL,
    coin                TEXT            NOT NULL,
    opened_at           BIGINT          NOT NULL,  -- ms epoch
    closed_at           BIGINT,                    -- NULL = still open
    entry_price         NUMERIC(30, 10) NOT NULL,
    max_size            NUMERIC(30, 10) NOT NULL,
    side                CHAR(1)         NOT NULL,  -- 'B' long, 'A' short
    realized_pnl        NUMERIC(30, 10) NOT NULL DEFAULT 0,
    total_fees          NUMERIC(30, 10) NOT NULL DEFAULT 0,
    total_funding       NUMERIC(30, 10) NOT NULL DEFAULT 0,
    hold_duration_ms    BIGINT
);

CREATE INDEX positions_wallet ON positions (wallet);
CREATE INDEX positions_wallet_coin ON positions (wallet, coin);
CREATE INDEX positions_closed_at ON positions (closed_at);

-- Computed leaderboard stats per wallet (refreshed every ~5 min)
CREATE TABLE wallet_stats (
    wallet          TEXT            PRIMARY KEY,
    computed_at     BIGINT          NOT NULL,
    total_pnl       NUMERIC(30, 10),
    win_rate        NUMERIC(8, 6),
    profit_factor   NUMERIC(12, 4),
    sharpe          NUMERIC(12, 4),
    max_drawdown    NUMERIC(8, 6),
    trade_count     INT,
    avg_hold_ms     BIGINT,
    trading_style   TEXT,           -- 'scalper' / 'day' / 'swing' / 'position'
    pnl_30d         NUMERIC(30, 10),
    pnl_90d         NUMERIC(30, 10),
    avg_win         NUMERIC(30, 10),
    avg_loss        NUMERIC(30, 10),
    total_fees      NUMERIC(30, 10),
    total_funding   NUMERIC(30, 10),
    long_pct        NUMERIC(8, 6)
);

-- Wallet registry and metadata
CREATE TABLE wallet_metadata (
    wallet              TEXT    PRIMARY KEY,
    first_seen_at       BIGINT  NOT NULL,
    last_active_at      BIGINT,
    earliest_fill_at    BIGINT,  -- oldest fill we have (null = not yet fetched)
    is_vault            BOOLEAN NOT NULL DEFAULT false,
    is_hlp              BOOLEAN NOT NULL DEFAULT false,
    claimed_name        TEXT,
    claimed_x_handle    TEXT,
    notes               TEXT
);
