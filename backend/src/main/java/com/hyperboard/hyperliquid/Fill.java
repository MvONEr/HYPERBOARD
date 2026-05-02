package com.hyperboard.hyperliquid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Fill(
    long tid,
    long oid,
    long time,
    String coin,
    String dir,
    String side,
    @JsonProperty("px") BigDecimal price,
    @JsonProperty("sz") BigDecimal size,
    BigDecimal fee,
    @JsonProperty("closedPnl") BigDecimal closedPnl,
    @JsonProperty("startPosition") BigDecimal startPosition,
    boolean crossed,
    @JsonProperty("feeToken") String feeToken,
    @JsonProperty("builderFee") BigDecimal builderFee) {}
