package com.hyperboard.api;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeaderboardDao {

  // Whitelist prevents SQL injection from the sort parameter
  private static final Set<String> ALLOWED_SORT =
      Set.of(
          "total_pnl",
          "pnl_30d",
          "pnl_90d",
          "win_rate",
          "sharpe",
          "profit_factor",
          "max_drawdown",
          "trade_count");

  private final NamedParameterJdbcTemplate jdbc;

  public LeaderboardDao(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<LeaderboardEntry> query(String sort, String style, int limit) {
    String sortCol = ALLOWED_SORT.contains(sort) ? sort : "total_pnl";
    // max_drawdown is better ranked ascending (lower is better), rest descending
    String direction = sortCol.equals("max_drawdown") ? "ASC" : "DESC";

    var params = new MapSqlParameterSource().addValue("limit", Math.min(limit, 500));

    String styleFilter = "WHERE trade_count >= 30";
    if (style != null && !style.isBlank()) {
      styleFilter += " AND trading_style = :style";
      params.addValue("style", style);
    }

    String sql =
        String.format(
            """
                SELECT
                    ROW_NUMBER() OVER (ORDER BY %s %s NULLS LAST) AS rank,
                    wallet, total_pnl, pnl_30d, pnl_90d,
                    win_rate, profit_factor, sharpe, max_drawdown,
                    trade_count, avg_hold_ms, trading_style, long_pct
                FROM wallet_stats
                %s
                ORDER BY %s %s NULLS LAST
                LIMIT :limit
                """,
            sortCol, direction, styleFilter, sortCol, direction);

    return jdbc.query(
        sql,
        params,
        (rs, row) ->
            new LeaderboardEntry(
                rs.getInt("rank"),
                rs.getString("wallet"),
                rs.getBigDecimal("total_pnl"),
                rs.getBigDecimal("pnl_30d"),
                rs.getBigDecimal("pnl_90d"),
                rs.getDouble("win_rate"),
                rs.getDouble("profit_factor"),
                rs.getDouble("sharpe"),
                rs.getDouble("max_drawdown"),
                rs.getInt("trade_count"),
                rs.getLong("avg_hold_ms"),
                rs.getString("trading_style"),
                rs.getDouble("long_pct")));
  }
}
