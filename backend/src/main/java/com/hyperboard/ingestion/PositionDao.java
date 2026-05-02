package com.hyperboard.ingestion;

import com.hyperboard.domain.ReconstructedPosition;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PositionDao {

  private final NamedParameterJdbcTemplate jdbc;

  public PositionDao(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ReconstructedPosition> readForWallet(String wallet) {
    String sql =
        """
                SELECT coin, side, opened_at, closed_at, entry_price, max_size,
                       realized_pnl, total_fees, hold_duration_ms
                FROM positions
                WHERE wallet = :wallet
                ORDER BY opened_at ASC
                """;

    return jdbc.query(
        sql,
        new MapSqlParameterSource("wallet", wallet),
        (rs, row) -> {
          long closedRaw = rs.getLong("closed_at");
          Long closedAt = rs.wasNull() ? null : closedRaw;
          long holdRaw = rs.getLong("hold_duration_ms");
          Long holdMs = rs.wasNull() ? null : holdRaw;
          return new ReconstructedPosition(
              rs.getString("coin"),
              rs.getString("side"),
              rs.getLong("opened_at"),
              closedAt,
              rs.getBigDecimal("entry_price"),
              rs.getBigDecimal("max_size"),
              rs.getBigDecimal("realized_pnl"),
              rs.getBigDecimal("total_fees"),
              holdMs);
        });
  }

  public int replaceForWallet(String wallet, List<ReconstructedPosition> positions) {
    jdbc.update(
        "DELETE FROM positions WHERE wallet = :wallet",
        new MapSqlParameterSource("wallet", wallet));

    if (positions.isEmpty()) return 0;

    String sql =
        """
                INSERT INTO positions
                  (wallet, coin, opened_at, closed_at, entry_price, max_size,
                   side, realized_pnl, total_fees, hold_duration_ms)
                VALUES
                  (:wallet, :coin, :openedAt, :closedAt, :entryPrice, :maxSize,
                   :side, :realizedPnl, :totalFees, :holdDurationMs)
                """;

    var params =
        positions.stream()
            .map(
                p ->
                    new MapSqlParameterSource()
                        .addValue("wallet", wallet)
                        .addValue("coin", p.coin())
                        .addValue("openedAt", p.openedAt())
                        .addValue("closedAt", p.closedAt())
                        .addValue("entryPrice", p.entryPrice())
                        .addValue("maxSize", p.maxSize())
                        .addValue("side", p.side())
                        .addValue("realizedPnl", p.realizedPnl())
                        .addValue("totalFees", p.totalFees())
                        .addValue("holdDurationMs", p.holdDurationMs()))
            .toArray(MapSqlParameterSource[]::new);

    int[] counts = jdbc.batchUpdate(sql, params);
    return java.util.Arrays.stream(counts).sum();
  }
}
