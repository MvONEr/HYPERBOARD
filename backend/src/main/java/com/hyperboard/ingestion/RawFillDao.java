package com.hyperboard.ingestion;

import com.hyperboard.hyperliquid.Fill;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RawFillDao {

  private final NamedParameterJdbcTemplate jdbc;

  public RawFillDao(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int upsert(String wallet, List<Fill> fills) {
    if (fills.isEmpty()) return 0;

    long fetchedAt = System.currentTimeMillis();
    String sql =
        """
                INSERT INTO raw_fills
                  (tid, wallet, coin, time, side, px, sz, fee, closed_pnl,
                   dir, start_position, crossed, oid, builder_fee, fee_token, fetched_at)
                VALUES
                  (:tid, :wallet, :coin, :time, :side, :px, :sz, :fee, :closedPnl,
                   :dir, :startPosition, :crossed, :oid, :builderFee, :feeToken, :fetchedAt)
                ON CONFLICT (tid, wallet) DO NOTHING
                """;

    var params =
        fills.stream()
            .map(
                f ->
                    new MapSqlParameterSource()
                        .addValue("tid", f.tid())
                        .addValue("wallet", wallet)
                        .addValue("coin", f.coin())
                        .addValue("time", f.time())
                        .addValue("side", f.side())
                        .addValue("px", f.price())
                        .addValue("sz", f.size())
                        .addValue("fee", f.fee())
                        .addValue("closedPnl", f.closedPnl())
                        .addValue("dir", f.dir())
                        .addValue("startPosition", f.startPosition())
                        .addValue("crossed", f.crossed())
                        .addValue("oid", f.oid())
                        .addValue("builderFee", f.builderFee())
                        .addValue("feeToken", f.feeToken())
                        .addValue("fetchedAt", fetchedAt))
            .toArray(MapSqlParameterSource[]::new);

    int[] counts = jdbc.batchUpdate(sql, params);
    return java.util.Arrays.stream(counts).sum();
  }

  public long earliestTime(String wallet) {
    String sql = "SELECT MIN(time) FROM raw_fills WHERE wallet = :wallet";
    Long result = jdbc.queryForObject(sql, new MapSqlParameterSource("wallet", wallet), Long.class);
    return result != null ? result : Long.MAX_VALUE;
  }

  public long count(String wallet) {
    String sql = "SELECT COUNT(*) FROM raw_fills WHERE wallet = :wallet";
    Long result = jdbc.queryForObject(sql, new MapSqlParameterSource("wallet", wallet), Long.class);
    return result != null ? result : 0L;
  }

  public FeeSplit feeSplit(String wallet) {
    String sql =
        """
                SELECT
                  COALESCE(SUM(CASE WHEN fee > 0 THEN fee END), 0) AS paid,
                  COALESCE(SUM(CASE WHEN fee < 0 THEN fee END), 0) AS rebated
                FROM raw_fills
                WHERE wallet = :wallet
                  AND coin NOT LIKE '@%'
                  AND coin NOT LIKE '%/%'
                """;
    return jdbc.query(
            sql,
            new MapSqlParameterSource("wallet", wallet),
            (rs, row) -> new FeeSplit(rs.getBigDecimal("paid"), rs.getBigDecimal("rebated")))
        .get(0);
  }

  /** paid = sum of positive fees, rebated = sum of negative fees (so rebated is negative). */
  public record FeeSplit(java.math.BigDecimal paid, java.math.BigDecimal rebated) {}
}
