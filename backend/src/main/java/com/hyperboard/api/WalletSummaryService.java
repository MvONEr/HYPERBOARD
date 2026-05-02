package com.hyperboard.api;

import com.hyperboard.api.WalletSummary.AssetBreakdown;
import com.hyperboard.api.WalletSummary.EquityPoint;
import com.hyperboard.domain.ReconstructedPosition;
import com.hyperboard.hyperliquid.HyperliquidClient;
import com.hyperboard.ingestion.PositionDao;
import com.hyperboard.ingestion.RawFillDao;
import com.hyperboard.ingestion.RawFillReader;
import com.hyperboard.ingestion.RawFundingDao;
import com.hyperboard.stats.WalletStatsDao;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WalletSummaryService {

  private static final MathContext MC = MathContext.DECIMAL128;
  private static final int RECENT_FILL_LIMIT = 20;

  private final WalletStatsDao statsDao;
  private final PositionDao positionDao;
  private final RawFillReader fillReader;
  private final RawFillDao fillDao;
  private final RawFundingDao fundingDao;
  private final HyperliquidClient client;

  public WalletSummaryService(
      WalletStatsDao statsDao,
      PositionDao positionDao,
      RawFillReader fillReader,
      RawFillDao fillDao,
      RawFundingDao fundingDao,
      HyperliquidClient client) {
    this.statsDao = statsDao;
    this.positionDao = positionDao;
    this.fillReader = fillReader;
    this.fillDao = fillDao;
    this.fundingDao = fundingDao;
    this.client = client;
  }

  public WalletSummary getSummary(String wallet) {
    var statsOpt = statsDao.findByWallet(wallet);
    if (statsOpt.isEmpty()) {
      return new WalletSummary(
          wallet, false, null, List.of(), List.of(), List.of(), null, null, null, null, List.of(),
          null);
    }

    var stats = statsOpt.get();
    var positions = positionDao.readForWallet(wallet);
    var recentFills = fillReader.readRecent(wallet, RECENT_FILL_LIMIT);
    var fundingSplit = fundingDao.fundingSplit(wallet);
    var feeSplit = fillDao.feeSplit(wallet);

    // Live state — best-effort. Falls back to stored account value if the API call fails.
    var liveState = client.clearinghouseState(wallet);
    var openPositions =
        liveState != null && liveState.assetPositions() != null
            ? liveState.assetPositions()
            : List.<com.hyperboard.hyperliquid.ClearinghouseState.AssetPosition>of();
    BigDecimal accountValue =
        liveState != null && liveState.marginSummary() != null
            ? liveState.marginSummary().accountValue()
            : stats.accountValue();

    return new WalletSummary(
        wallet,
        true,
        stats,
        equityCurve(positions),
        assetBreakdown(positions),
        recentFills,
        fundingSplit.earned(),
        fundingSplit.paid(),
        feeSplit.paid(),
        feeSplit.rebated(),
        openPositions,
        accountValue);
  }

  /** Cumulative realized PnL series, one point per closed position ordered by close time. */
  private List<EquityPoint> equityCurve(List<ReconstructedPosition> positions) {
    var closed =
        positions.stream()
            .filter(p -> !p.isOpen())
            .sorted(Comparator.comparingLong(ReconstructedPosition::closedAt))
            .toList();

    List<EquityPoint> points = new ArrayList<>(closed.size());
    BigDecimal cum = BigDecimal.ZERO;
    for (var p : closed) {
      cum = cum.add(p.realizedPnl(), MC);
      points.add(new EquityPoint(p.closedAt(), cum));
    }
    return points;
  }

  /** Per-coin trade count, realized PnL, and notional volume (closed positions only). */
  private List<AssetBreakdown> assetBreakdown(List<ReconstructedPosition> positions) {
    record Acc(int trades, BigDecimal pnl, BigDecimal volume) {}
    var byCoin = new LinkedHashMap<String, Acc>();

    for (var p : positions) {
      if (p.isOpen()) continue;
      var prev = byCoin.getOrDefault(p.coin(), new Acc(0, BigDecimal.ZERO, BigDecimal.ZERO));
      var notional = p.entryPrice().multiply(p.maxSize(), MC);
      byCoin.put(
          p.coin(),
          new Acc(
              prev.trades() + 1,
              prev.pnl().add(p.realizedPnl(), MC),
              prev.volume().add(notional, MC)));
    }

    return byCoin.entrySet().stream()
        .map(
            e ->
                new AssetBreakdown(
                    e.getKey(), e.getValue().trades(), e.getValue().pnl(), e.getValue().volume()))
        .sorted(Comparator.comparing(AssetBreakdown::realizedPnl).reversed())
        .toList();
  }
}
