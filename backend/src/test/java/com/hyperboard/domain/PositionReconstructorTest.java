package com.hyperboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyperboard.hyperliquid.Fill;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionReconstructorTest {

  private final PositionReconstructor reconstructor = new PositionReconstructor();

  // Helper to build fills without noise fields
  private Fill fill(long time, String side, String px, String sz, String closedPnl) {
    return new Fill(
        time,
        0L,
        time,
        "BTC",
        "dir",
        side,
        new BigDecimal(px),
        new BigDecimal(sz),
        BigDecimal.ZERO,
        new BigDecimal(closedPnl),
        BigDecimal.ZERO,
        false,
        "USDC",
        null);
  }

  // --- Basic open and close ---

  @Test
  void singleLong_openThenClose_producesOneClosedPosition() {
    var fills =
        List.of(
            fill(1000, "B", "100", "10", "0"), // open long 10 @ 100
            fill(2000, "A", "110", "10", "100")); // close long 10 @ 110

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(1);
    var p = positions.get(0);
    assertThat(p.isOpen()).isFalse();
    assertThat(p.side()).isEqualTo("B");
    assertThat(p.realizedPnl()).isEqualByComparingTo("100"); // (110-100)*10
    assertThat(p.holdDurationMs()).isEqualTo(1000L);
  }

  @Test
  void singleShort_openThenClose_producesCorrectPnl() {
    var fills =
        List.of(
            fill(1000, "A", "100", "10", "0"), // open short 10 @ 100
            fill(2000, "B", "90", "10", "100")); // close short 10 @ 90

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(1);
    var p = positions.get(0);
    assertThat(p.realizedPnl()).isEqualByComparingTo("100"); // (100-90)*10
  }

  @Test
  void losingLong_producesNegativePnl() {
    var fills = List.of(fill(1000, "B", "100", "10", "0"), fill(2000, "A", "90", "10", "-100"));

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(1);
    assertThat(positions.get(0).realizedPnl()).isEqualByComparingTo("-100");
  }

  // --- Partial closes ---

  @Test
  void partialClose_thenFullClose_producesSingleClosedPosition() {
    var fills =
        List.of(
            fill(1000, "B", "100", "10", "0"), // open long 10 @ 100
            fill(2000, "A", "110", "5", "50"), // close half @ 110 → PnL = 50
            fill(
                3000, "A", "120", "5",
                "50")); // close rest @ 120 → PnL = 50 more (entry still 100, wait)

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(1);
    var p = positions.get(0);
    assertThat(p.isOpen()).isFalse();
    // (110-100)*5 + (120-100)*5 = 50 + 100 = 150
    assertThat(p.realizedPnl()).isEqualByComparingTo("150");
  }

  // --- Adding to position (scale-in) ---

  @Test
  void scaleIn_weightedAverageEntryIsCorrect() {
    var fills =
        List.of(
            fill(1000, "B", "100", "10", "0"), // buy 10 @ 100
            fill(2000, "B", "200", "10", "0"), // buy 10 more @ 200 → avg entry = 150
            fill(3000, "A", "200", "20", "0")); // close all @ 200

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(1);
    // PnL = (200 - 150) * 20 = 1000
    assertThat(positions.get(0).realizedPnl()).isEqualByComparingTo("1000");
  }

  // --- Position flip (long → short in one fill) ---

  @Test
  void positionFlip_closesLongAndOpensShort() {
    var fills =
        List.of(
            fill(1000, "B", "100", "10", "0"), // open long 10 @ 100
            fill(
                2000, "A", "110", "20",
                "100")); // sell 20: closes 10 long (PnL=100), opens 10 short

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(2);

    var closed = positions.stream().filter(p -> !p.isOpen()).findFirst().orElseThrow();
    assertThat(closed.side()).isEqualTo("B");
    assertThat(closed.realizedPnl()).isEqualByComparingTo("100");

    var open = positions.stream().filter(ReconstructedPosition::isOpen).findFirst().orElseThrow();
    assertThat(open.side()).isEqualTo("A"); // now short
    assertThat(open.entryPrice()).isEqualByComparingTo("110");
  }

  // --- Open position (no close fill yet) ---

  @Test
  void noClosingFill_positionRemainsOpen() {
    var fills = List.of(fill(1000, "B", "100", "10", "0"));

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(1);
    assertThat(positions.get(0).isOpen()).isTrue();
    assertThat(positions.get(0).closedAt()).isNull();
    assertThat(positions.get(0).realizedPnl()).isEqualByComparingTo("0");
  }

  // --- Multiple coins are independent ---

  @Test
  void multipleCoins_reconstructedIndependently() {
    var btcOpen =
        new Fill(
            1000,
            0,
            1000,
            "BTC",
            "d",
            "B",
            bd("100"),
            bd("1"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            "USDC",
            null);
    var btcClose =
        new Fill(
            2000,
            0,
            2000,
            "BTC",
            "d",
            "A",
            bd("110"),
            bd("1"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            "USDC",
            null);
    var ethOpen =
        new Fill(
            1000,
            0,
            1000,
            "ETH",
            "d",
            "B",
            bd("200"),
            bd("5"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            "USDC",
            null);
    var ethClose =
        new Fill(
            2000,
            0,
            2000,
            "ETH",
            "d",
            "A",
            bd("190"),
            bd("5"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            "USDC",
            null);

    var positions = reconstructor.reconstruct(List.of(btcOpen, btcClose, ethOpen, ethClose));

    assertThat(positions).hasSize(2);
    var btc = positions.stream().filter(p -> p.coin().equals("BTC")).findFirst().orElseThrow();
    var eth = positions.stream().filter(p -> p.coin().equals("ETH")).findFirst().orElseThrow();

    assertThat(btc.realizedPnl()).isEqualByComparingTo("10"); // (110-100)*1
    assertThat(eth.realizedPnl()).isEqualByComparingTo("-50"); // (190-200)*5
  }

  // --- maxSize tracks the peak size ---

  @Test
  void maxSize_tracksLargestSizeDuringPosition() {
    var fills =
        List.of(
            fill(1000, "B", "100", "10", "0"), // open 10
            fill(2000, "B", "100", "5", "0"), // scale to 15
            fill(3000, "A", "100", "8", "0"), // reduce to 7
            fill(4000, "A", "100", "7", "0")); // close

    var positions = reconstructor.reconstruct(fills);

    assertThat(positions).hasSize(1);
    assertThat(positions.get(0).maxSize()).isEqualByComparingTo("15");
  }

  // --- Empty fills ---

  @Test
  void emptyFills_returnsEmptyList() {
    assertThat(reconstructor.reconstruct(List.of())).isEmpty();
  }

  private static BigDecimal bd(String val) {
    return new BigDecimal(val);
  }
}
