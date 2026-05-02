package com.hyperboard.ingestion;

import com.hyperboard.hyperliquid.Fill;
import com.hyperboard.hyperliquid.HyperliquidClient;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WalletIngestionService {

  private static final Logger log = LoggerFactory.getLogger(WalletIngestionService.class);

  // Keep the 10k most recent fills — enough for all stats
  private static final int MAX_FILLS = 10_000;

  private final HyperliquidClient client;
  private final RawFillDao fillDao;
  private final RawFundingDao fundingDao;

  public WalletIngestionService(
      HyperliquidClient client, RawFillDao fillDao, RawFundingDao fundingDao) {
    this.client = client;
    this.fillDao = fillDao;
    this.fundingDao = fundingDao;
  }

  public IngestionResult backfill(String wallet) {
    log.info("Starting backfill for {}", wallet);

    // userFills returns all fills with no time filter — no gap problem
    List<Fill> all = client.userFills(wallet);

    // Sort newest-first, cap at MAX_FILLS
    var toStore =
        all.stream()
            .sorted(Comparator.comparingLong(Fill::time).reversed())
            .limit(MAX_FILLS)
            .toList();

    int totalFills = toStore.isEmpty() ? 0 : fillDao.upsert(wallet, toStore);

    // Fetch funding from earliest fill onward
    int totalFunding = 0;
    long earliestFill = fillDao.earliestTime(wallet);
    if (earliestFill < Long.MAX_VALUE) {
      var funding = client.userFunding(wallet, earliestFill);
      totalFunding = fundingDao.upsert(wallet, funding);
    }

    log.info(
        "Backfill done for {}: {} fills, {} funding payments", wallet, totalFills, totalFunding);
    return new IngestionResult(wallet, totalFills, totalFunding);
  }

  public record IngestionResult(String wallet, int fillsStored, int fundingStored) {}
}
