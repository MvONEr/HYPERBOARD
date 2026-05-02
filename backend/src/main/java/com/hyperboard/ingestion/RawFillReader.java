package com.hyperboard.ingestion;

import com.hyperboard.hyperliquid.Fill;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RawFillReader {

  private final NamedParameterJdbcTemplate jdbc;

  public RawFillReader(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Excludes Hyperliquid spot fills: "@<index>" and "<sym>/USDC". HIP-3 perps ("dex:SYM") stay.
  private static final String PERP_ONLY = "AND coin NOT LIKE '@%' AND coin NOT LIKE '%/%'";

  public List<Fill> readForWallet(String wallet) {
    String sql =
        """
                SELECT tid, oid, time, coin, dir, side, px, sz, fee,
                       closed_pnl, start_position, crossed, fee_token, builder_fee
                FROM raw_fills
                WHERE wallet = :wallet
                """
            + " "
            + PERP_ONLY
            + """

                ORDER BY time ASC
                """;

    return jdbc.query(sql, new MapSqlParameterSource("wallet", wallet), this::mapFill);
  }

  /** Most recent fills first, capped at limit. */
  public List<Fill> readRecent(String wallet, int limit) {
    String sql =
        """
                SELECT tid, oid, time, coin, dir, side, px, sz, fee,
                       closed_pnl, start_position, crossed, fee_token, builder_fee
                FROM raw_fills
                WHERE wallet = :wallet
                """
            + " "
            + PERP_ONLY
            + """

                ORDER BY time DESC
                LIMIT :limit
                """;

    var params = new MapSqlParameterSource().addValue("wallet", wallet).addValue("limit", limit);
    return jdbc.query(sql, params, this::mapFill);
  }

  private Fill mapFill(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Fill(
        rs.getLong("tid"),
        rs.getLong("oid"),
        rs.getLong("time"),
        rs.getString("coin"),
        rs.getString("dir"),
        rs.getString("side"),
        rs.getBigDecimal("px"),
        rs.getBigDecimal("sz"),
        rs.getBigDecimal("fee"),
        rs.getBigDecimal("closed_pnl"),
        rs.getBigDecimal("start_position"),
        rs.getBoolean("crossed"),
        rs.getString("fee_token"),
        rs.getBigDecimal("builder_fee"));
  }
}
