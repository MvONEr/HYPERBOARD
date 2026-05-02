package com.hyperboard.api;

import java.math.BigDecimal;

public record LeaderboardEntry(
    int rank,
    String wallet,
    BigDecimal totalPnl,
    BigDecimal pnl30d,
    BigDecimal pnl90d,
    double winRate,
    double profitFactor,
    double sharpe,
    double maxDrawdown,
    int tradeCount,
    long avgHoldMs,
    String tradingStyle,
    double longPct) {}
