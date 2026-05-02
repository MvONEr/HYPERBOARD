package com.hyperboard.ingestion;

import com.hyperboard.hyperliquid.FundingPayment;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RawFundingDao {

  private final NamedParameterJdbcTemplate jdbc;

  public RawFundingDao(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int upsert(String wallet, List<FundingPayment> payments) {
    if (payments.isEmpty()) return 0;

    long fetchedAt = System.currentTimeMillis();
    String sql =
        """
                INSERT INTO raw_funding (wallet, time, coin, amount, fetched_at)
                VALUES (:wallet, :time, :coin, :amount, :fetchedAt)
                ON CONFLICT (wallet, time, coin) DO NOTHING
                """;

    var params =
        payments.stream()
            .filter(p -> p.delta() != null)
            .map(
                p ->
                    new MapSqlParameterSource()
                        .addValue("wallet", wallet)
                        .addValue("time", p.time())
                        .addValue("coin", p.delta().coin())
                        .addValue("amount", p.delta().usdc())
                        .addValue("fetchedAt", fetchedAt))
            .toArray(MapSqlParameterSource[]::new);

    int[] counts = jdbc.batchUpdate(sql, params);
    return java.util.Arrays.stream(counts).sum();
  }

  public java.math.BigDecimal totalFunding(String wallet) {
    String sql = "SELECT COALESCE(SUM(amount), 0) FROM raw_funding WHERE wallet = :wallet";
    return jdbc.queryForObject(
        sql, new MapSqlParameterSource("wallet", wallet), java.math.BigDecimal.class);
  }

  public FundingSplit fundingSplit(String wallet) {
    String sql =
        """
                SELECT
                  COALESCE(SUM(CASE WHEN amount > 0 THEN amount END), 0) AS earned,
                  COALESCE(SUM(CASE WHEN amount < 0 THEN amount END), 0) AS paid
                FROM raw_funding
                WHERE wallet = :wallet
                """;
    return jdbc.query(
            sql,
            new MapSqlParameterSource("wallet", wallet),
            (rs, row) -> new FundingSplit(rs.getBigDecimal("earned"), rs.getBigDecimal("paid")))
        .get(0);
  }

  /** earned = sum of positive funding, paid = sum of negative funding (so paid is negative). */
  public record FundingSplit(java.math.BigDecimal earned, java.math.BigDecimal paid) {}
}
