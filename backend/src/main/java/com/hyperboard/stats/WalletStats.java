package com.hyperboard.stats;

import java.math.BigDecimal;

public record WalletStats(
    String wallet,
    long computedAt,
    BigDecimal totalPnl,
    BigDecimal pnl30d,
    BigDecimal pnl90d,
    BigDecimal totalFees,
    BigDecimal totalFunding,
    double winRate,
    double profitFactor,
    double sharpe,
    double maxDrawdown, // 0.0–1.0 (e.g. 0.25 = 25% drawdown)
    int tradeCount,
    long avgHoldMs,
    String tradingStyle, // scalper / day / swing / position
    BigDecimal avgWin,
    BigDecimal avgLoss,
    double longPct,
    BigDecimal accountValue) {}
