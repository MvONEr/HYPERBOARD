package com.hyperboard.hyperliquid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetInfo(String name, int szDecimals, int maxLeverage) {}
