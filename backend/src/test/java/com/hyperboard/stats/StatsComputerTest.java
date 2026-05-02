package com.hyperboard.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hyperboard.domain.ReconstructedPosition;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsComputerTest {

  private final StatsComputer computer = new StatsComputer();

  private ReconstructedPosition pos(
      String coin,
      String side,
      long openedAt,
      long closedAt,
      String entryPrice,
      String maxSize,
      String realizedPnl,
      String fees) {
    long holdMs = closedAt - openedAt;
    return new ReconstructedPosition(
        coin,
        side,
        openedAt,
        closedAt,
        new BigDecimal(entryPrice),
        new BigDecimal(maxSize),
        new BigDecimal(realizedPnl),
        new BigDecimal(fees),
        holdMs);
  }

  private ReconstructedPosition openPos(
      String coin, String side, long openedAt, String entryPrice, String maxSize) {
    return new ReconstructedPosition(
        coin,
        side,
        openedAt,
        null,
        new BigDecimal(entryPrice),
        new BigDecimal(maxSize),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null);
  }

  @Test
  void emptyPositions_returnsZeroStats() {
    var stats = computer.compute("wallet", List.of(), BigDecimal.ZERO, null);

    assertThat(stats.tradeCount()).isZero();
    assertThat(stats.winRate()).isZero();
    assertThat(stats.totalPnl()).isEqualByComparingTo("0");
  }

  @Test
  void winRate_computedFromClosedPositionsOnly() {
    long now = System.currentTimeMillis();
    var positions =
        List.of(
            pos("BTC", "B", now - 5000, now - 4000, "100", "1", "50", "0"), // win
            pos("ETH", "B", now - 5000, now - 4000, "100", "1", "30", "0"), // win
            pos("SOL", "B", now - 5000, now - 4000, "100", "1", "-20", "0"), // loss
            openPos("DOGE", "B", now - 1000, "1", "100")); // open, excluded

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    assertThat(stats.tradeCount()).isEqualTo(3);
    assertThat(stats.winRate()).isCloseTo(2.0 / 3.0, within(0.001));
  }

  @Test
  void totalPnl_sumsOnlyClosedPositions() {
    long now = System.currentTimeMillis();
    var positions =
        List.of(
            pos("BTC", "B", now - 5000, now - 4000, "100", "1", "100", "0"),
            pos("ETH", "B", now - 5000, now - 4000, "100", "1", "-30", "0"),
            openPos("SOL", "B", now - 1000, "100", "1")); // open, not counted

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    assertThat(stats.totalPnl()).isEqualByComparingTo("70");
  }

  @Test
  void profitFactor_grossWinsOverGrossLosses() {
    long now = System.currentTimeMillis();
    var positions =
        List.of(
            pos("BTC", "B", now - 5000, now - 4000, "100", "1", "100", "0"),
            pos("ETH", "B", now - 5000, now - 4000, "100", "1", "50", "0"),
            pos("SOL", "B", now - 5000, now - 4000, "100", "1", "-25", "0"));

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    // grossWins=150, grossLosses=25 → profitFactor=6
    assertThat(stats.profitFactor()).isCloseTo(6.0, within(0.01));
  }

  @Test
  void avgWin_avgLoss_correctSign() {
    long now = System.currentTimeMillis();
    var positions =
        List.of(
            pos("BTC", "B", now - 5000, now - 4000, "100", "1", "100", "0"),
            pos("ETH", "B", now - 5000, now - 4000, "100", "1", "200", "0"),
            pos("SOL", "B", now - 5000, now - 4000, "100", "1", "-60", "0"));

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    assertThat(stats.avgWin()).isEqualByComparingTo("150"); // (100+200)/2
    assertThat(stats.avgLoss()).isEqualByComparingTo("-60"); // single loss
  }

  @Test
  void maxDrawdown_peakToTrough() {
    long now = System.currentTimeMillis();
    // Equity curve: +100, +100, -80 → cumulative: 100, 200, 120
    // Peak=200, trough=120 → drawdown = 80/200 = 0.4
    var positions =
        List.of(
            pos("A", "B", now - 3000, now - 2500, "100", "1", "100", "0"),
            pos("B", "B", now - 2500, now - 2000, "100", "1", "100", "0"),
            pos("C", "B", now - 2000, now - 1500, "100", "1", "-80", "0"));

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    assertThat(stats.maxDrawdown()).isCloseTo(0.4, within(0.001));
  }

  @Test
  void totalFunding_includedFromParameter() {
    var stats = computer.compute("wallet", List.of(), new BigDecimal("-500"), null);
    assertThat(stats.totalFunding()).isEqualByComparingTo("-500");
  }

  @Test
  void tradingStyle_scalper_shortHoldHighFrequency() {
    long now = System.currentTimeMillis();
    // 20 trades, all held 10 minutes, over 1 day → tradesPerDay=20, avgHold=10min < 1hr
    var positions = new java.util.ArrayList<ReconstructedPosition>();
    for (int i = 0; i < 20; i++) {
      long open = now - (20 - i) * 30 * 60 * 1000L;
      positions.add(pos("BTC", "B", open, open + 10 * 60 * 1000L, "100", "1", "1", "0"));
    }

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    assertThat(stats.tradingStyle()).isEqualTo("scalper");
  }

  @Test
  void tradingStyle_swing_multiDayHold() {
    long now = System.currentTimeMillis();
    long threeDays = 3L * 24 * 60 * 60 * 1000;
    var positions =
        List.of(pos("BTC", "B", now - threeDays - 1000, now - 1000, "100", "1", "50", "0"));

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    assertThat(stats.tradingStyle()).isEqualTo("swing");
  }

  @Test
  void longPct_ratioOfLongPositions() {
    long now = System.currentTimeMillis();
    var positions =
        List.of(
            pos("BTC", "B", now - 5000, now - 4000, "100", "1", "10", "0"),
            pos("ETH", "B", now - 5000, now - 4000, "100", "1", "10", "0"),
            pos("SOL", "A", now - 5000, now - 4000, "100", "1", "10", "0"));

    var stats = computer.compute("wallet", positions, BigDecimal.ZERO, null);

    assertThat(stats.longPct()).isCloseTo(2.0 / 3.0, within(0.001));
  }
}
