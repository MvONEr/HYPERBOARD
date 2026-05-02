import Link from "next/link";
import { notFound } from "next/navigation";
import {
  fetchWalletSummary,
  fmtHold,
  fmtPct,
  fmtRelative,
  fmtTime,
  fmtUsd,
  fmtUsdSigned,
} from "@/lib/api";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { EquityCurve } from "@/components/wallet/equity-curve";

const STYLE_LABEL: Record<string, string> = {
  scalper: "Scalper",
  day: "Day trader",
  swing: "Swing",
  position: "Position",
};

function pnlColor(n: number) {
  return n > 0 ? "text-green-500" : n < 0 ? "text-red-500" : "text-foreground";
}

function CoinLabel({ coin }: { coin: string }) {
  const colon = coin.indexOf(":");
  if (colon < 0) return <span className="font-medium">{coin}</span>;
  return (
    <span className="inline-flex items-baseline gap-1.5">
      <span className="font-medium">{coin.slice(colon + 1)}</span>
      <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
        {coin.slice(0, colon)}
      </span>
    </span>
  );
}

function StatCard({
  label,
  value,
  sub,
  tone,
}: {
  label: string;
  value: string;
  sub?: string;
  tone?: "pos" | "neg" | "warn";
}) {
  const valueClass =
    tone === "pos"
      ? "text-green-500"
      : tone === "neg"
        ? "text-red-500"
        : tone === "warn"
          ? "text-orange-500"
          : "text-foreground";
  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="text-xs text-muted-foreground uppercase tracking-wide">{label}</div>
      <div className={`mt-1 text-xl font-semibold ${valueClass}`}>{value}</div>
      {sub && <div className="text-xs text-muted-foreground mt-1">{sub}</div>}
    </div>
  );
}

export default async function WalletPage({
  params,
}: {
  params: Promise<{ address: string }>;
}) {
  const { address } = await params;

  if (!/^0x[a-fA-F0-9]{40}$/.test(address)) notFound();

  const summary = await fetchWalletSummary(address);

  if (!summary.tracked || !summary.stats) {
    return (
      <main className="max-w-4xl mx-auto px-4 py-8">
        <Link href="/leaderboard" className="text-sm text-muted-foreground hover:underline">
          ← Back to leaderboard
        </Link>
        <h1 className="mt-4 text-2xl font-semibold">Wallet not tracked yet</h1>
        <p className="mt-2 text-muted-foreground">
          We don&apos;t have stats for{" "}
          <span className="font-mono">{address}</span> yet. Tracked wallets are
          seeded from Hyperliquid&apos;s public leaderboard with quality
          filters.
        </p>
      </main>
    );
  }

  const s = summary.stats;
  const profitFactorDisplay =
    s.profitFactor >= 99 ? "∞" : s.profitFactor.toFixed(2);

  return (
    <main className="max-w-7xl mx-auto px-4 py-8 space-y-8">
      {/* Header */}
      <div>
        <Link href="/leaderboard" className="text-sm text-muted-foreground hover:underline">
          ← Back to leaderboard
        </Link>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <h1 className="font-mono text-lg sm:text-xl break-all">{summary.wallet}</h1>
          {s.tradingStyle && (
            <Badge variant="outline">{STYLE_LABEL[s.tradingStyle] ?? s.tradingStyle}</Badge>
          )}
          {summary.accountValue != null && (
            <span className="text-sm text-muted-foreground">
              Account: <span className="text-foreground font-medium">{fmtUsd(summary.accountValue)}</span>
            </span>
          )}
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Stats updated {fmtRelative(s.computedAt)} · Computed from up to 10,000
          most recent fills
        </p>
      </div>

      {/* Stat grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatCard
          label="All-time PnL"
          value={fmtUsdSigned(s.totalPnl)}
          tone={s.totalPnl >= 0 ? "pos" : "neg"}
        />
        <StatCard
          label="30d PnL"
          value={fmtUsdSigned(s.pnl30d)}
          tone={s.pnl30d >= 0 ? "pos" : "neg"}
        />
        <StatCard
          label="90d PnL"
          value={fmtUsdSigned(s.pnl90d)}
          tone={s.pnl90d >= 0 ? "pos" : "neg"}
        />
        <StatCard label="Win rate" value={fmtPct(s.winRate)} />
        <StatCard label="Profit factor" value={profitFactorDisplay} />
        <StatCard
          label="Sharpe-like"
          value={s.sharpe.toFixed(2)}
          sub="per-trade ROI / stdev"
        />
        <StatCard label="Max drawdown" value={fmtPct(s.maxDrawdown)} tone="warn" />
        <StatCard label="Trade count" value={String(s.tradeCount)} />
        <StatCard label="Avg hold" value={fmtHold(s.avgHoldMs)} />
        <StatCard
          label="Avg win"
          value={fmtUsd(s.avgWin)}
          tone={s.avgWin > 0 ? "pos" : undefined}
        />
        <StatCard
          label="Avg loss"
          value={fmtUsd(s.avgLoss)}
          tone={s.avgLoss < 0 ? "neg" : undefined}
        />
        <StatCard
          label="Long / short"
          value={`${Math.round(s.longPct * 100)}% / ${Math.round((1 - s.longPct) * 100)}%`}
        />
      </div>

      {/* Equity curve */}
      <section>
        <h2 className="text-sm font-medium uppercase tracking-wide text-muted-foreground mb-2">
          Cumulative realized PnL
        </h2>
        <div className="rounded-lg border bg-card p-4">
          <EquityCurve points={summary.equityCurve} />
        </div>
      </section>

      {/* Open positions */}
      {summary.openPositions.length > 0 && (
        <section>
          <h2 className="text-sm font-medium uppercase tracking-wide text-muted-foreground mb-2">
            Open positions
          </h2>
          <div className="rounded-lg border overflow-hidden">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Coin</TableHead>
                  <TableHead>Side</TableHead>
                  <TableHead className="text-right">Size</TableHead>
                  <TableHead className="text-right">Entry</TableHead>
                  <TableHead className="text-right">Notional</TableHead>
                  <TableHead className="text-right">Unrealized PnL</TableHead>
                  <TableHead className="text-right">ROE</TableHead>
                  <TableHead className="text-right">Liq price</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {summary.openPositions.map((p) => {
                  const isLong = p.position.szi > 0;
                  return (
                    <TableRow key={p.position.coin}>
                      <TableCell><CoinLabel coin={p.position.coin} /></TableCell>
                      <TableCell>
                        <span className={isLong ? "text-green-500" : "text-red-500"}>
                          {isLong ? "Long" : "Short"}
                        </span>
                      </TableCell>
                      <TableCell className="text-right font-mono text-sm">
                        {Math.abs(p.position.szi)}
                      </TableCell>
                      <TableCell className="text-right font-mono text-sm">
                        {p.position.entryPx}
                      </TableCell>
                      <TableCell className="text-right font-mono text-sm">
                        {fmtUsd(p.position.positionValue)}
                      </TableCell>
                      <TableCell className={`text-right font-mono text-sm ${pnlColor(p.position.unrealizedPnl)}`}>
                        {fmtUsdSigned(p.position.unrealizedPnl)}
                      </TableCell>
                      <TableCell className={`text-right font-mono text-sm ${pnlColor(p.position.returnOnEquity)}`}>
                        {fmtPct(p.position.returnOnEquity)}
                      </TableCell>
                      <TableCell className="text-right font-mono text-sm text-muted-foreground">
                        {p.position.liquidationPx != null ? (
                          p.position.liquidationPx
                        ) : (
                          <span
                            className="cursor-help underline decoration-dotted decoration-muted-foreground/50 underline-offset-2"
                            title="Cross-margin long with enough equity that liquidation would require the asset to fall to ~$0. Hyperliquid reports no liq price in this case."
                          >
                            ~0
                          </span>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        </section>
      )}

      {/* Two-column: asset breakdown + funding/fees */}
      <div className="grid lg:grid-cols-3 gap-6">
        <section className="lg:col-span-2">
          <h2 className="text-sm font-medium uppercase tracking-wide text-muted-foreground mb-2">
            By asset
          </h2>
          <div className="rounded-lg border overflow-hidden">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Coin</TableHead>
                  <TableHead className="text-right">Trades</TableHead>
                  <TableHead className="text-right">Volume</TableHead>
                  <TableHead className="text-right">Realized PnL</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {summary.assetBreakdown.map((a) => (
                  <TableRow key={a.coin}>
                    <TableCell><CoinLabel coin={a.coin} /></TableCell>
                    <TableCell className="text-right text-muted-foreground">
                      {a.tradeCount}
                    </TableCell>
                    <TableCell className="text-right text-muted-foreground">
                      {fmtUsd(a.volume)}
                    </TableCell>
                    <TableCell className={`text-right font-medium ${pnlColor(a.realizedPnl)}`}>
                      {fmtUsdSigned(a.realizedPnl)}
                    </TableCell>
                  </TableRow>
                ))}
                {summary.assetBreakdown.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center text-muted-foreground py-6">
                      No closed positions yet
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </section>

        <section className="space-y-4">
          <div>
            <h2 className="text-sm font-medium uppercase tracking-wide text-muted-foreground mb-2">
              Funding
            </h2>
            <div className="rounded-lg border bg-card p-4 space-y-3">
              <div className="flex justify-between items-baseline">
                <span className="text-sm text-muted-foreground">Earned</span>
                <span className="text-green-500 font-medium">
                  {summary.fundingEarned != null ? fmtUsdSigned(summary.fundingEarned) : "—"}
                </span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-sm text-muted-foreground">Paid</span>
                <span className="text-red-500 font-medium">
                  {summary.fundingPaid != null ? fmtUsdSigned(summary.fundingPaid) : "—"}
                </span>
              </div>
              <div className="border-t pt-3 flex justify-between items-baseline">
                <span className="text-sm">Net</span>
                <span className={`font-medium ${pnlColor(s.totalFunding)}`}>
                  {fmtUsdSigned(s.totalFunding)}
                </span>
              </div>
            </div>
          </div>

          <div>
            <h2 className="text-sm font-medium uppercase tracking-wide text-muted-foreground mb-2">
              Fees
            </h2>
            <div className="rounded-lg border bg-card p-4 space-y-3">
              <div className="flex justify-between items-baseline">
                <span className="text-sm text-muted-foreground">Paid</span>
                <span className="text-red-500 font-medium">
                  {summary.feesPaid != null ? `−${fmtUsd(summary.feesPaid)}` : "—"}
                </span>
              </div>
              <div className="flex justify-between items-baseline">
                <span className="text-sm text-muted-foreground">Rebated</span>
                <span className="text-green-500 font-medium">
                  {summary.feesRebated != null
                    ? `+${fmtUsd(Math.abs(summary.feesRebated))}`
                    : "—"}
                </span>
              </div>
              <div className="border-t pt-3 flex justify-between items-baseline">
                <span className="text-sm">Net</span>
                <span className={`font-medium ${s.totalFees > 0 ? "text-red-500" : s.totalFees < 0 ? "text-green-500" : ""}`}>
                  {s.totalFees > 0
                    ? `−${fmtUsd(s.totalFees)}`
                    : s.totalFees < 0
                      ? `+${fmtUsd(Math.abs(s.totalFees))}`
                      : fmtUsd(0)}
                </span>
              </div>
            </div>
          </div>
        </section>
      </div>

      {/* Recent activity */}
      <section>
        <h2 className="text-sm font-medium uppercase tracking-wide text-muted-foreground mb-2">
          Recent activity
        </h2>
        <div className="rounded-lg border overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Time</TableHead>
                <TableHead>Coin</TableHead>
                <TableHead>Action</TableHead>
                <TableHead className="text-right">Size</TableHead>
                <TableHead className="text-right">Price</TableHead>
                <TableHead className="text-right">Realized PnL</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {summary.recentFills.map((f) => (
                <TableRow key={`${f.tid}-${f.coin}`}>
                  <TableCell className="text-muted-foreground text-sm">
                    {fmtTime(f.time)}
                  </TableCell>
                  <TableCell><CoinLabel coin={f.coin} /></TableCell>
                  <TableCell className="text-sm">{f.dir}</TableCell>
                  <TableCell className="text-right font-mono text-sm">{f.sz}</TableCell>
                  <TableCell className="text-right font-mono text-sm">{f.px}</TableCell>
                  <TableCell className={`text-right font-mono text-sm ${pnlColor(f.closedPnl)}`}>
                    {f.closedPnl !== 0 ? fmtUsdSigned(f.closedPnl) : "—"}
                  </TableCell>
                </TableRow>
              ))}
              {summary.recentFills.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-muted-foreground py-6">
                    No fills recorded
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </section>
    </main>
  );
}
