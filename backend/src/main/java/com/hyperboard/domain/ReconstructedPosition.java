package com.hyperboard.domain;

import java.math.BigDecimal;

public record ReconstructedPosition(
    String coin,
    String side, // "B" = long, "A" = short
    long openedAt,
    Long closedAt, // null = still open
    BigDecimal entryPrice,
    BigDecimal maxSize,
    BigDecimal realizedPnl,
    BigDecimal totalFees,
    Long holdDurationMs) {

  public boolean isOpen() {
    return closedAt == null;
  }
}
