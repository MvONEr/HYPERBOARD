package com.hyperboard.hyperliquid;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HyperliquidClient {

  private static final Logger log = LoggerFactory.getLogger(HyperliquidClient.class);
  private static final String INFO_URL = "https://api.hyperliquid.xyz/info";

  private final HttpClient http;
  private final ObjectMapper mapper;

  public HyperliquidClient(ObjectMapper mapper) {
    this.mapper = mapper;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  public List<Fill> userFills(String walletAddress) {
    String body =
        """
                {"type":"userFills","user":"%s","aggregateByTime":false}
                """
            .formatted(walletAddress)
            .strip();

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(INFO_URL))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString(body))
              .timeout(Duration.ofSeconds(30))
              .build();

      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("userFills failed for {}: HTTP {}", walletAddress, response.statusCode());
        return List.of();
      }

      return mapper.readerForListOf(Fill.class).readValue(response.body());

    } catch (Exception e) {
      log.error("userFills error for {}", walletAddress, e);
      return List.of();
    }
  }

  public List<FundingPayment> userFunding(String walletAddress, long startTimeMs) {
    String body =
        """
        {"type":"userFunding","user":"%s","startTime":%d}
        """
            .formatted(walletAddress, startTimeMs)
            .strip();

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(INFO_URL))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString(body))
              .timeout(Duration.ofSeconds(30))
              .build();

      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("userFunding failed for {}: HTTP {}", walletAddress, response.statusCode());
        return List.of();
      }

      return mapper.readerForListOf(FundingPayment.class).readValue(response.body());

    } catch (Exception e) {
      log.error("userFunding error for {}", walletAddress, e);
      return List.of();
    }
  }

  public List<Fill> userFillsByTime(String walletAddress, long startTimeMs, long endTimeMs) {
    String body =
        """
        {"type":"userFillsByTime","user":"%s","startTime":%d,"endTime":%d}
        """
            .formatted(walletAddress, startTimeMs, endTimeMs)
            .strip();

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(INFO_URL))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString(body))
              .timeout(Duration.ofSeconds(30))
              .build();

      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("userFillsByTime failed for {}: HTTP {}", walletAddress, response.statusCode());
        return List.of();
      }

      return mapper.readerForListOf(Fill.class).readValue(response.body());

    } catch (Exception e) {
      log.error("userFillsByTime error for {}", walletAddress, e);
      return List.of();
    }
  }

  public List<LeaderboardRow> fetchLeaderboard() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://stats-data.hyperliquid.xyz/Mainnet/leaderboard"))
              .GET()
              .timeout(Duration.ofSeconds(30))
              .build();

      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("fetchLeaderboard failed: HTTP {}", response.statusCode());
        return List.of();
      }

      var root = mapper.readTree(response.body());
      return mapper.readerForListOf(LeaderboardRow.class).readValue(root.get("leaderboardRows"));

    } catch (Exception e) {
      log.error("fetchLeaderboard error", e);
      return List.of();
    }
  }

  public List<AssetInfo> meta() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(INFO_URL))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString("{\"type\":\"meta\"}"))
              .timeout(Duration.ofSeconds(30))
              .build();

      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("meta failed: HTTP {}", response.statusCode());
        return List.of();
      }

      var root = mapper.readTree(response.body());
      return mapper.readerForListOf(AssetInfo.class).readValue(root.get("universe"));

    } catch (Exception e) {
      log.error("meta error", e);
      return List.of();
    }
  }

  public ClearinghouseState clearinghouseState(String walletAddress) {
    String body =
        """
        {"type":"clearinghouseState","user":"%s"}
        """
            .formatted(walletAddress)
            .strip();

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(INFO_URL))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString(body))
              .timeout(Duration.ofSeconds(30))
              .build();

      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error(
            "clearinghouseState failed for {}: HTTP {}", walletAddress, response.statusCode());
        return null;
      }

      return mapper.readValue(response.body(), ClearinghouseState.class);

    } catch (Exception e) {
      log.error("clearinghouseState error for {}", walletAddress, e);
      return null;
    }
  }
}
