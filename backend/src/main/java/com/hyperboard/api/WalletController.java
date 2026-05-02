package com.hyperboard.api;

import com.hyperboard.domain.PositionReconstructor;
import com.hyperboard.domain.ReconstructedPosition;
import com.hyperboard.hyperliquid.ClearinghouseState;
import com.hyperboard.hyperliquid.Fill;
import com.hyperboard.hyperliquid.FundingPayment;
import com.hyperboard.hyperliquid.HyperliquidClient;
import com.hyperboard.ingestion.PositionProcessingService;
import com.hyperboard.ingestion.PositionProcessingService.ProcessingResult;
import com.hyperboard.ingestion.WalletIngestionService;
import com.hyperboard.ingestion.WalletIngestionService.IngestionResult;
import com.hyperboard.stats.StatsComputationService;
import com.hyperboard.stats.WalletStats;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WalletController {

  private final HyperliquidClient hyperliquid;
  private final PositionReconstructor reconstructor;
  private final WalletIngestionService ingestion;
  private final PositionProcessingService processing;
  private final StatsComputationService statsComputation;
  private final WalletSummaryService summaryService;

  public WalletController(
      HyperliquidClient hyperliquid,
      PositionReconstructor reconstructor,
      WalletIngestionService ingestion,
      PositionProcessingService processing,
      StatsComputationService statsComputation,
      WalletSummaryService summaryService) {
    this.hyperliquid = hyperliquid;
    this.reconstructor = reconstructor;
    this.ingestion = ingestion;
    this.processing = processing;
    this.statsComputation = statsComputation;
    this.summaryService = summaryService;
  }

  @GetMapping("/wallet/{address}/summary")
  public WalletSummary summary(@PathVariable String address) {
    return summaryService.getSummary(address);
  }

  @GetMapping("/wallet/{address}/fills")
  public List<Fill> fills(@PathVariable String address) {
    return hyperliquid.userFills(address);
  }

  @GetMapping("/wallet/{address}/funding")
  public List<FundingPayment> funding(
      @PathVariable String address, @RequestParam(defaultValue = "0") long startTime) {
    return hyperliquid.userFunding(address, startTime);
  }

  @GetMapping("/wallet/{address}/state")
  public ClearinghouseState state(@PathVariable String address) {
    return hyperliquid.clearinghouseState(address);
  }

  @GetMapping("/wallet/{address}/positions")
  public List<ReconstructedPosition> positions(@PathVariable String address) {
    List<Fill> fills = hyperliquid.userFills(address);
    return reconstructor.reconstruct(fills);
  }

  @PostMapping("/wallet/{address}/ingest")
  public IngestionResult ingest(@PathVariable String address) {
    return ingestion.backfill(address);
  }

  @PostMapping("/wallet/{address}/process")
  public ProcessingResult process(@PathVariable String address) {
    return processing.process(address);
  }

  @PostMapping("/wallet/{address}/stats")
  public WalletStats stats(@PathVariable String address) {
    return statsComputation.computeAndStore(address);
  }
}
