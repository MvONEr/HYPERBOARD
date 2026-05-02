package com.hyperboard.hyperliquid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LeaderboardRow(
    @JsonProperty("ethAddress") String ethAddress,
    @JsonProperty("accountValue") BigDecimal accountValue,
    @JsonProperty("windowPerformances") List<List<Object>> windowPerformances) {

  public WindowPerf perf(String window) {
    if (windowPerformances == null) return null;
    for (var entry : windowPerformances) {
      if (entry.size() == 2 && window.equals(entry.get(0))) {
        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) entry.get(1);
        return new WindowPerf(
            new BigDecimal(map.get("pnl").toString()),
            new BigDecimal(map.get("roi").toString()),
            new BigDecimal(map.get("vlm").toString()));
      }
    }
    return null;
  }

  public record WindowPerf(BigDecimal pnl, BigDecimal roi, BigDecimal vlm) {}
}
