package com.hyperboard.stats;

import com.hyperboard.domain.PositionReconstructor;
import com.hyperboard.hyperliquid.HyperliquidClient;
import com.hyperboard.ingestion.PositionDao;
import com.hyperboard.ingestion.RawFillReader;
import com.hyperboard.ingestion.RawFundingDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StatsComputationService {

  private static final Logger log = LoggerFactory.getLogger(StatsComputationService.class);

  private final RawFillReader fillReader;
  private final PositionReconstructor reconstructor;
  private final PositionDao positionDao;
  private final RawFundingDao fundingDao;
  private final StatsComputer computer;
  private final WalletStatsDao statsDao;
  private final HyperliquidClient client;

  public StatsComputationService(
      RawFillReader fillReader,
      PositionReconstructor reconstructor,
      PositionDao positionDao,
      RawFundingDao fundingDao,
      StatsComputer computer,
      WalletStatsDao statsDao,
      HyperliquidClient client) {
    this.fillReader = fillReader;
    this.reconstructor = reconstructor;
    this.positionDao = positionDao;
    this.fundingDao = fundingDao;
    this.computer = computer;
    this.statsDao = statsDao;
    this.client = client;
  }

  public WalletStats computeAndStore(String wallet) {
    log.info("Computing stats for {}", wallet);

    var fills = fillReader.readForWallet(wallet);
    var positions = reconstructor.reconstruct(fills);
    positionDao.replaceForWallet(wallet, positions);

    var totalFunding = fundingDao.totalFunding(wallet);

    var state = client.clearinghouseState(wallet);
    var accountValue =
        (state != null && state.marginSummary() != null)
            ? state.marginSummary().accountValue()
            : null;

    var stats = computer.compute(wallet, positions, totalFunding, accountValue);
    statsDao.upsert(stats);

    log.info(
        "Stats done for {}: tradeCount={} winRate={}% pnl={}",
        wallet, stats.tradeCount(), String.format("%.1f", stats.winRate() * 100), stats.totalPnl());
    return stats;
  }
}
