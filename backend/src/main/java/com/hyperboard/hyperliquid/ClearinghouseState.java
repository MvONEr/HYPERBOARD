package com.hyperboard.hyperliquid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClearinghouseState(
    MarginSummary marginSummary, List<AssetPosition> assetPositions, long time) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MarginSummary(
      BigDecimal accountValue,
      @JsonProperty("totalNtlPos") BigDecimal totalNotionalPosition,
      BigDecimal totalMarginUsed) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AssetPosition(Position position) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(
        String coin,
        @JsonProperty("szi") BigDecimal size, // positive = long, negative = short
        @JsonProperty("entryPx") BigDecimal entryPrice,
        BigDecimal positionValue,
        BigDecimal unrealizedPnl,
        @JsonProperty("returnOnEquity") BigDecimal roe,
        @JsonProperty("liquidationPx") BigDecimal liquidationPrice,
        CumFunding cumFunding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CumFunding(BigDecimal allTime, BigDecimal sinceOpen) {}
  }
}
