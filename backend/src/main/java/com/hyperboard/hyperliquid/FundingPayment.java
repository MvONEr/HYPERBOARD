package com.hyperboard.hyperliquid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FundingPayment(long time, Delta delta) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Delta(
      String coin,
      BigDecimal usdc, // negative = paid, positive = received
      BigDecimal szi, // position size at funding time
      BigDecimal fundingRate) {}
}
