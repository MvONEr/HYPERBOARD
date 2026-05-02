package com.hyperboard.stats;

import com.hyperboard.domain.ReconstructedPosition;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

@Component
public class StatsComputer {

  private static final MathContext MC = MathContext.DECIMAL128;
  private static final long MS_30D = 30L * 24 * 60 * 60 * 1000;
  private static final long MS_90D = 90L * 24 * 60 * 60 * 1000;
  private static final long MS_1D = 24L * 60 * 60 * 1000;
  private static final int MIN_DAYS_FOR_SHARPE = 5;
  private static final double TRADING_DAYS_PER_YEAR = 365; // crypto trades 24/7

  public WalletStats compute(
      String wallet,
      List<ReconstructedPosition> positions,
      BigDecimal totalFunding,
      BigDecimal accountValue) {
    long now = System.currentTimeMillis();

    List<ReconstructedPosition> closed =
        positions.stream()
            .filter(p -> !p.isOpen())
            .sorted(Comparator.comparingLong(ReconstructedPosition::closedAt))
            .toList();

    int tradeCount = closed.size();

    // ── PnL totals ──────────────────────────────────────────────────────
    BigDecimal totalPnl =
        closed.stream()
            .map(ReconstructedPosition::realizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal pnl30d =
        closed.stream()
            .filter(p -> p.closedAt() >= now - MS_30D)
            .map(ReconstructedPosition::realizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal pnl90d =
        closed.stream()
            .filter(p -> p.closedAt() >= now - MS_90D)
            .map(ReconstructedPosition::realizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalFees =
        positions.stream()
            .map(ReconstructedPosition::totalFees)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // ── Win rate / avg win / avg loss / profit factor ────────────────────
    List<ReconstructedPosition> wins =
        closed.stream().filter(p -> p.realizedPnl().signum() > 0).toList();
    List<ReconstructedPosition> losses =
        closed.stream().filter(p -> p.realizedPnl().signum() < 0).toList();

    double winRate = tradeCount == 0 ? 0.0 : (double) wins.size() / tradeCount;

    BigDecimal avgWin =
        wins.isEmpty()
            ? BigDecimal.ZERO
            : wins.stream()
                .map(ReconstructedPosition::realizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(wins.size()), MC);

    BigDecimal avgLoss =
        losses.isEmpty()
            ? BigDecimal.ZERO
            : losses.stream()
                .map(ReconstructedPosition::realizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(losses.size()), MC);

    double grossWins = wins.stream().mapToDouble(p -> p.realizedPnl().doubleValue()).sum();
    double grossLosses =
        Math.abs(losses.stream().mapToDouble(p -> p.realizedPnl().doubleValue()).sum());
    // Cap at 9999 so it fits in numeric(12,4); frontend shows ∞ for values > 99 anyway
    double profitFactor =
        grossLosses == 0
            ? (grossWins > 0 ? 9999.0 : 0.0)
            : Math.min(grossWins / grossLosses, 9999.0);

    // ── Sharpe-like (mean per-position ROI / stdev) ──────────────────────
    double sharpe = 0.0;
    if (tradeCount >= 2) {
      double[] returns =
          closed.stream()
              .mapToDouble(
                  p -> {
                    double notional = p.entryPrice().doubleValue() * p.maxSize().doubleValue();
                    return notional == 0 ? 0.0 : p.realizedPnl().doubleValue() / notional;
                  })
              .toArray();
      double mean = java.util.Arrays.stream(returns).average().orElse(0.0);
      double variance =
          java.util.Arrays.stream(returns).map(r -> (r - mean) * (r - mean)).average().orElse(0.0);
      double stdev = Math.sqrt(variance);
      sharpe = stdev == 0 ? 0.0 : Math.max(-9999.0, Math.min(9999.0, mean / stdev));
    }

    // ── Max drawdown (peak-to-trough on cumulative PnL curve) ───────────
    double maxDrawdown = 0.0;
    if (!closed.isEmpty()) {
      double cumPnl = 0.0;
      double peak = 0.0;
      for (var p : closed) {
        cumPnl += p.realizedPnl().doubleValue();
        if (cumPnl > peak) peak = cumPnl;
        if (peak > 0) {
          double dd = (peak - cumPnl) / peak;
          if (dd > maxDrawdown) maxDrawdown = dd;
        }
      }
      // Clamp to 1.0 (100%) — losing more than your peak gains is still "blown up"
      maxDrawdown = Math.min(1.0, maxDrawdown);
    }

    // ── Hold duration & trading style ────────────────────────────────────
    OptionalDouble avgHoldOpt =
        closed.stream()
            .filter(p -> p.holdDurationMs() != null)
            .mapToLong(ReconstructedPosition::holdDurationMs)
            .average();
    long avgHoldMs = (long) avgHoldOpt.orElse(0);

    long firstTradeMs = closed.isEmpty() ? now : closed.get(0).openedAt();
    double daysActive = Math.max(1.0, (now - firstTradeMs) / (double) (24 * 60 * 60 * 1000));
    double tradesPerDay = tradeCount / daysActive;

    String tradingStyle = classifyStyle(avgHoldMs, tradesPerDay);

    // ── Long % ───────────────────────────────────────────────────────────
    long longs = closed.stream().filter(p -> p.side().equals("B")).count();
    double longPct = tradeCount == 0 ? 0.5 : (double) longs / tradeCount;

    return new WalletStats(
        wallet,
        now,
        totalPnl,
        pnl30d,
        pnl90d,
        totalFees,
        totalFunding != null ? totalFunding : BigDecimal.ZERO,
        winRate,
        profitFactor,
        sharpe,
        maxDrawdown,
        tradeCount,
        avgHoldMs,
        tradingStyle,
        avgWin,
        avgLoss,
        longPct,
        accountValue);
  }

  private String classifyStyle(long avgHoldMs, double tradesPerDay) {
    long hours = avgHoldMs / (60 * 60 * 1000);
    if (hours < 1 && tradesPerDay > 5) return "scalper";
    if (hours < 24) return "day";
    if (hours < 7 * 24) return "swing";
    return "position";
  }
}
