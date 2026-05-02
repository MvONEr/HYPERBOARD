package com.hyperboard.api;

import com.hyperboard.hyperliquid.ClearinghouseState.AssetPosition;
import com.hyperboard.hyperliquid.Fill;
import com.hyperboard.stats.WalletStats;
import java.math.BigDecimal;
import java.util.List;

public record WalletSummary(
    String wallet,
    boolean tracked,
    WalletStats stats,
    List<EquityPoint> equityCurve,
    List<AssetBreakdown> assetBreakdown,
    List<Fill> recentFills,
    BigDecimal fundingEarned,
    BigDecimal fundingPaid,
    BigDecimal feesPaid,
    BigDecimal feesRebated,
    List<AssetPosition> openPositions,
    BigDecimal accountValue) {

  public record EquityPoint(long time, BigDecimal cumPnl) {}

  public record AssetBreakdown(
      String coin, int tradeCount, BigDecimal realizedPnl, BigDecimal volume) {}
}
