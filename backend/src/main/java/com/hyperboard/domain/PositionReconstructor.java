package com.hyperboard.domain;

import com.hyperboard.hyperliquid.Fill;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PositionReconstructor {

  private static final MathContext MC = MathContext.DECIMAL128;
  private static final BigDecimal EPSILON = new BigDecimal("0.000001");

  public List<ReconstructedPosition> reconstruct(List<Fill> fills) {
    Map<String, List<Fill>> byCoin = fills.stream().collect(Collectors.groupingBy(Fill::coin));

    List<ReconstructedPosition> result = new ArrayList<>();
    for (var entry : byCoin.entrySet()) {
      result.addAll(reconstructCoin(entry.getValue()));
    }
    return result;
  }

  private List<ReconstructedPosition> reconstructCoin(List<Fill> fills) {
    List<Fill> sorted = fills.stream().sorted(Comparator.comparingLong(Fill::time)).toList();

    List<ReconstructedPosition> closed = new ArrayList<>();
    PositionState current = null;

    for (Fill fill : sorted) {
      // positive = long, negative = short
      BigDecimal signedSize = fill.side().equals("B") ? fill.size() : fill.size().negate();

      if (current == null) {
        BigDecimal startPos = fill.startPosition();
        if (startPos.abs().compareTo(EPSILON) > 0) {
          // There was an open position before our data window starts.
          // Bootstrap state using startPosition and infer entry price from closedPnl.
          current = bootstrapState(fill, startPos, signedSize);
          // Fall through to process this fill against the bootstrapped state.
        } else {
          // Normal first fill for this coin — opens a new position.
          current = new PositionState(fill, signedSize, false);
          continue;
        }
      }

      boolean sameDirection = signedSize.signum() == current.size.signum();

      if (sameDirection) {
        // Adding to position — update weighted average entry price
        BigDecimal newSize = current.size.add(signedSize, MC);
        current.entryPrice =
            current
                .entryPrice
                .multiply(current.size.abs(), MC)
                .add(fill.price().multiply(signedSize.abs(), MC), MC)
                .divide(newSize.abs(), MC);
        current.size = newSize;
        current.maxSize = current.maxSize.max(newSize.abs());
        current.totalFees = current.totalFees.add(fill.fee(), MC);

      } else {
        // Reducing or flipping
        BigDecimal closeAmount = signedSize.abs().min(current.size.abs());
        BigDecimal pnlMultiplier =
            current.size.signum() > 0 ? BigDecimal.ONE : BigDecimal.ONE.negate();
        BigDecimal pnl =
            closeAmount
                .multiply(fill.price().subtract(current.entryPrice, MC), MC)
                .multiply(pnlMultiplier, MC);

        current.realizedPnl = current.realizedPnl.add(pnl, MC);
        current.totalFees = current.totalFees.add(fill.fee(), MC);

        if (signedSize.abs().compareTo(current.size.abs()) >= 0) {
          // Fully closed or flipped — close the current position
          closed.add(current.toPosition(fill.coin(), fill.time()));
          current = null;

          // Any excess size opens a new position in the opposite direction
          BigDecimal overflow = signedSize.abs().subtract(closeAmount, MC);
          if (overflow.compareTo(EPSILON) > 0) {
            BigDecimal overflowSigned = signedSize.signum() > 0 ? overflow : overflow.negate();
            current = new PositionState(fill, overflowSigned, false);
          }
        } else {
          // Partial reduce
          current.size = current.size.add(signedSize, MC);
        }
      }
    }

    // Any remaining open position
    if (current != null) {
      closed.add(current.toPosition(sorted.get(sorted.size() - 1).coin(), null));
    }

    return closed;
  }

  /**
   * Creates a PositionState bootstrapped from a fill's startPosition when we start mid-stream.
   * Entry price is inferred from closedPnl so PnL computation stays accurate.
   */
  private PositionState bootstrapState(Fill fill, BigDecimal startPos, BigDecimal signedFill) {
    BigDecimal entryPrice;
    BigDecimal closedPnl = fill.closedPnl();

    if (closedPnl != null && closedPnl.abs().compareTo(EPSILON) > 0) {
      // Infer entry price: pnl = closeAmount * (exitPrice - entryPrice) * pnlMultiplier
      BigDecimal closeAmount = signedFill.abs().min(startPos.abs());
      if (closeAmount.compareTo(EPSILON) > 0) {
        BigDecimal pnlMultiplier = startPos.signum() > 0 ? BigDecimal.ONE : BigDecimal.ONE.negate();
        // entryPrice = exitPrice - pnl / closeAmount / pnlMultiplier
        entryPrice =
            fill.price().subtract(closedPnl.divide(closeAmount, MC).divide(pnlMultiplier, MC), MC);
      } else {
        entryPrice = fill.price();
      }
    } else {
      entryPrice = fill.price();
    }

    return new PositionState(startPos, entryPrice, fill.time(), startPos.signum() > 0 ? "B" : "A");
  }

  private static class PositionState {
    BigDecimal size;
    BigDecimal entryPrice;
    BigDecimal maxSize;
    BigDecimal realizedPnl = BigDecimal.ZERO;
    BigDecimal totalFees;
    final long openedAt;
    final String side;
    // True when position was open before our fill history starts; hold time is unreliable.
    final boolean synthetic;

    PositionState(Fill fill, BigDecimal signedSize, boolean synthetic) {
      this.size = signedSize;
      this.entryPrice = fill.price();
      this.maxSize = signedSize.abs();
      this.totalFees = fill.fee();
      this.openedAt = fill.time();
      this.side = fill.side();
      this.synthetic = synthetic;
    }

    // Constructor for bootstrapped (synthetic) positions
    PositionState(BigDecimal startPos, BigDecimal entryPrice, long openedAt, String side) {
      this.size = startPos;
      this.entryPrice = entryPrice;
      this.maxSize = startPos.abs();
      this.totalFees = BigDecimal.ZERO;
      this.openedAt = openedAt;
      this.side = side;
      this.synthetic = true;
    }

    ReconstructedPosition toPosition(String coin, Long closedAt) {
      // Suppress hold time for synthetic positions — we don't know the true open time
      Long holdMs = (closedAt != null && !synthetic) ? closedAt - openedAt : null;
      return new ReconstructedPosition(
          coin, side, openedAt, closedAt, entryPrice, maxSize, realizedPnl, totalFees, holdMs);
    }
  }
}
