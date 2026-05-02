package com.hyperboard.ingestion;

import com.hyperboard.hyperliquid.HyperliquidClient;
import com.hyperboard.hyperliquid.LeaderboardRow;
import com.hyperboard.stats.StatsComputationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WalletSeedingService {

  private static final Logger log = LoggerFactory.getLogger(WalletSeedingService.class);

  // Quality filters — keeps the leaderboard meaningful
  private static final BigDecimal MIN_ALL_TIME_VOLUME = new BigDecimal("100000"); // $100k traded
  private static final BigDecimal MIN_ACCOUNT_VALUE = new BigDecimal("20000"); // $20k equity
  private static final int MIN_MONTH_ROI_SIGN = 0; // no filter on sign, just presence

  // Rate limiting — each wallet triggers several API calls
  private static final long DELAY_MS = 2_000;

  private final HyperliquidClient client;
  private final WalletIngestionService ingestion;
  private final StatsComputationService stats;

  public WalletSeedingService(
      HyperliquidClient client, WalletIngestionService ingestion, StatsComputationService stats) {
    this.client = client;
    this.ingestion = ingestion;
    this.stats = stats;
  }

  /** Fetch Hyperliquid's leaderboard, apply quality filters, return qualifying wallets. */
  public List<LeaderboardRow> fetchQualifyingWallets(int limit) {
    log.info("Fetching Hyperliquid leaderboard...");
    var rows = client.fetchLeaderboard();
    log.info("Raw leaderboard: {} wallets", rows.size());

    var qualified = rows.stream().filter(this::passesQualityFilter).limit(limit).toList();

    log.info("After quality filters: {} wallets", qualified.size());
    return qualified;
  }

  private boolean passesQualityFilter(LeaderboardRow row) {
    // Must have meaningful account value
    if (row.accountValue() == null || row.accountValue().compareTo(MIN_ACCOUNT_VALUE) < 0) {
      return false;
    }
    // Must have all-time data with sufficient volume
    var allTime = row.perf("allTime");
    if (allTime == null || allTime.vlm().compareTo(MIN_ALL_TIME_VOLUME) < 0) {
      return false;
    }
    // Must have month window data (filters brand-new accounts)
    var month = row.perf("month");
    return month != null;
  }

  /** Full pipeline: fetch qualifying wallets, ingest fills, compute stats. */
  public SeedResult seedFromLeaderboard(int limit) {
    var wallets = fetchQualifyingWallets(limit);
    return seedWallets(wallets.stream().map(LeaderboardRow::ethAddress).toList());
  }

  /** Seed a specific list of wallet addresses. */
  public SeedResult seedWallets(List<String> addresses) {
    log.info("Seeding {} wallets", addresses.size());

    List<String> succeeded = new ArrayList<>();
    List<String> failed = new ArrayList<>();

    for (int i = 0; i < addresses.size(); i++) {
      String wallet = addresses.get(i).strip();
      if (wallet.isBlank()) continue;

      log.info("[{}/{}] {}", i + 1, addresses.size(), wallet);
      try {
        ingestion.backfill(wallet);
        stats.computeAndStore(wallet);
        succeeded.add(wallet);
      } catch (Exception e) {
        log.error("Failed {}: {}", wallet, e.getMessage());
        failed.add(wallet);
      }

      if (i < addresses.size() - 1) {
        try {
          Thread.sleep(DELAY_MS);
        } catch (InterruptedException ignored) {
        }
      }
    }

    log.info("Seeding done: {} ok, {} failed", succeeded.size(), failed.size());
    return new SeedResult(succeeded.size(), failed.size(), failed);
  }

  public record SeedResult(int succeeded, int failed, List<String> failedWallets) {}
}
