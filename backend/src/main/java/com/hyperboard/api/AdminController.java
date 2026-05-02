package com.hyperboard.api;

import com.hyperboard.ingestion.WalletSeedingService;
import com.hyperboard.ingestion.WalletSeedingService.SeedResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final WalletSeedingService seeder;

  public AdminController(WalletSeedingService seeder) {
    this.seeder = seeder;
  }

  /** Preview which wallets pass quality filters without seeding them. */
  @GetMapping("/qualifying-wallets")
  public List<QualifyingWallet> qualifyingWallets(@RequestParam(defaultValue = "500") int limit) {
    return seeder.fetchQualifyingWallets(limit).stream()
        .map(
            row -> {
              var allTime = row.perf("allTime");
              var month = row.perf("month");
              return new QualifyingWallet(
                  row.ethAddress(),
                  row.accountValue(),
                  allTime != null ? allTime.pnl() : null,
                  allTime != null ? allTime.vlm() : null,
                  month != null ? month.roi() : null);
            })
        .toList();
  }

  /** Full seed: pull leaderboard, apply filters, ingest + compute stats. */
  @PostMapping("/seed")
  public SeedResult seed(@RequestParam(defaultValue = "30") int limit) {
    return seeder.seedFromLeaderboard(limit);
  }

  /** Seed a specific list of wallet addresses (for testing or manual overrides). */
  @PostMapping("/seed-wallets")
  public SeedResult seedWallets(@RequestBody List<String> wallets) {
    return seeder.seedWallets(wallets);
  }

  public record QualifyingWallet(
      String wallet,
      java.math.BigDecimal accountValue,
      java.math.BigDecimal allTimePnl,
      java.math.BigDecimal allTimeVolume,
      java.math.BigDecimal monthRoi) {}
}
