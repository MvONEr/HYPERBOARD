package com.hyperboard.ingestion;

import com.hyperboard.domain.PositionReconstructor;
import com.hyperboard.domain.ReconstructedPosition;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PositionProcessingService {

  private static final Logger log = LoggerFactory.getLogger(PositionProcessingService.class);

  private final RawFillReader fillReader;
  private final PositionReconstructor reconstructor;
  private final PositionDao positionDao;

  public PositionProcessingService(
      RawFillReader fillReader, PositionReconstructor reconstructor, PositionDao positionDao) {
    this.fillReader = fillReader;
    this.reconstructor = reconstructor;
    this.positionDao = positionDao;
  }

  public ProcessingResult process(String wallet) {
    log.info("Processing positions for {}", wallet);

    var fills = fillReader.readForWallet(wallet);
    if (fills.isEmpty()) {
      log.warn("No fills in DB for {} — run ingest first", wallet);
      return new ProcessingResult(wallet, 0, 0, 0);
    }

    List<ReconstructedPosition> positions = reconstructor.reconstruct(fills);
    long closed = positions.stream().filter(p -> !p.isOpen()).count();
    long open = positions.stream().filter(ReconstructedPosition::isOpen).count();

    int stored = positionDao.replaceForWallet(wallet, positions);
    log.info(
        "Processed {} fills → {} positions ({} closed, {} open) for {}",
        fills.size(),
        stored,
        closed,
        open,
        wallet);

    return new ProcessingResult(wallet, fills.size(), (int) closed, (int) open);
  }

  public record ProcessingResult(
      String wallet, int fillsRead, int closedPositions, int openPositions) {}
}
