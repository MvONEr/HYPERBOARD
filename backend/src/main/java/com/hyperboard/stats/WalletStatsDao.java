package com.hyperboard.stats;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletStatsDao {

  private final NamedParameterJdbcTemplate jdbc;

  public WalletStatsDao(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<WalletStats> findByWallet(String wallet) {
    String sql =
        """
                SELECT wallet, computed_at, total_pnl, pnl_30d, pnl_90d, total_fees, total_funding,
                       win_rate, profit_factor, sharpe, max_drawdown, trade_count, avg_hold_ms,
                       trading_style, avg_win, avg_loss, long_pct, account_value
                FROM wallet_stats
                WHERE wallet = :wallet
                """;

    var rows =
        jdbc.query(
            sql,
            new MapSqlParameterSource("wallet", wallet),
            (rs, row) ->
                new WalletStats(
                    rs.getString("wallet"),
                    rs.getLong("computed_at"),
                    rs.getBigDecimal("total_pnl"),
                    rs.getBigDecimal("pnl_30d"),
                    rs.getBigDecimal("pnl_90d"),
                    rs.getBigDecimal("total_fees"),
                    rs.getBigDecimal("total_funding"),
                    rs.getDouble("win_rate"),
                    rs.getDouble("profit_factor"),
                    rs.getDouble("sharpe"),
                    rs.getDouble("max_drawdown"),
                    rs.getInt("trade_count"),
                    rs.getLong("avg_hold_ms"),
                    rs.getString("trading_style"),
                    rs.getBigDecimal("avg_win"),
                    rs.getBigDecimal("avg_loss"),
                    rs.getDouble("long_pct"),
                    rs.getBigDecimal("account_value")));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public void upsert(WalletStats s) {
    String sql =
        """
                INSERT INTO wallet_stats
                  (wallet, computed_at, total_pnl, pnl_30d, pnl_90d, total_fees, total_funding,
                   win_rate, profit_factor, sharpe, max_drawdown, trade_count, avg_hold_ms,
                   trading_style, avg_win, avg_loss, long_pct, account_value)
                VALUES
                  (:wallet, :computedAt, :totalPnl, :pnl30d, :pnl90d, :totalFees, :totalFunding,
                   :winRate, :profitFactor, :sharpe, :maxDrawdown, :tradeCount, :avgHoldMs,
                   :tradingStyle, :avgWin, :avgLoss, :longPct, :accountValue)
                ON CONFLICT (wallet) DO UPDATE SET
                  computed_at    = EXCLUDED.computed_at,
                  total_pnl      = EXCLUDED.total_pnl,
                  pnl_30d        = EXCLUDED.pnl_30d,
                  pnl_90d        = EXCLUDED.pnl_90d,
                  total_fees     = EXCLUDED.total_fees,
                  total_funding  = EXCLUDED.total_funding,
                  win_rate       = EXCLUDED.win_rate,
                  profit_factor  = EXCLUDED.profit_factor,
                  sharpe         = EXCLUDED.sharpe,
                  max_drawdown   = EXCLUDED.max_drawdown,
                  trade_count    = EXCLUDED.trade_count,
                  avg_hold_ms    = EXCLUDED.avg_hold_ms,
                  trading_style  = EXCLUDED.trading_style,
                  avg_win        = EXCLUDED.avg_win,
                  avg_loss       = EXCLUDED.avg_loss,
                  long_pct       = EXCLUDED.long_pct,
                  account_value  = COALESCE(EXCLUDED.account_value, wallet_stats.account_value)
                """;

    jdbc.update(
        sql,
        new MapSqlParameterSource()
            .addValue("wallet", s.wallet())
            .addValue("computedAt", s.computedAt())
            .addValue("totalPnl", s.totalPnl())
            .addValue("pnl30d", s.pnl30d())
            .addValue("pnl90d", s.pnl90d())
            .addValue("totalFees", s.totalFees())
            .addValue("totalFunding", s.totalFunding())
            .addValue("winRate", s.winRate())
            .addValue("profitFactor", s.profitFactor())
            .addValue("sharpe", s.sharpe())
            .addValue("maxDrawdown", s.maxDrawdown())
            .addValue("tradeCount", s.tradeCount())
            .addValue("avgHoldMs", s.avgHoldMs())
            .addValue("tradingStyle", s.tradingStyle())
            .addValue("avgWin", s.avgWin())
            .addValue("avgLoss", s.avgLoss())
            .addValue("longPct", s.longPct())
            .addValue("accountValue", s.accountValue()));
  }
}
