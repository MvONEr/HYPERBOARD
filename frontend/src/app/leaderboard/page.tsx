import { fetchLeaderboard, fmtUsd, fmtPct, fmtHold, shortWallet, type SortField } from "@/lib/api";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import Link from "next/link";

const SORT_OPTIONS: { value: SortField; label: string }[] = [
  { value: "total_pnl", label: "All-time PnL" },
  { value: "pnl_30d", label: "30d PnL" },
  { value: "win_rate", label: "Win rate" },
  { value: "profit_factor", label: "Profit factor" },
  { value: "sharpe", label: "Sharpe" },
  { value: "max_drawdown", label: "Max drawdown" },
  { value: "trade_count", label: "Trades" },
];

export default async function LeaderboardPage({
  searchParams,
}: {
  searchParams: Promise<{ sort?: string }>;
}) {
  const params = await searchParams;
  const sort = (params.sort as SortField) ?? "total_pnl";

  const entries = await fetchLeaderboard(sort, undefined, 30);

  return (
    <main className="max-w-7xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight">Hyperboard</h1>
        <p className="text-muted-foreground mt-1">
          Hyperliquid trader leaderboard — honest stats, no cherry-picking.
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-2 mb-6">
        <div className="flex items-center gap-1">
          <span className="text-sm text-muted-foreground mr-1">Sort:</span>
          {SORT_OPTIONS.map((opt) => (
            <Link
              key={opt.value}
              href={`/leaderboard?sort=${opt.value}`}
              className={`px-3 py-1 rounded-full text-sm border transition-colors ${
                sort === opt.value
                  ? "bg-foreground text-background border-foreground"
                  : "border-border hover:border-foreground"
              }`}
            >
              {opt.label}
            </Link>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="rounded-lg border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12">#</TableHead>
              <TableHead>Wallet</TableHead>
              <TableHead className="text-right">All-time PnL</TableHead>
              <TableHead className="text-right">30d PnL</TableHead>
              <TableHead className="text-right">Win rate</TableHead>
              <TableHead className="text-right">Max DD</TableHead>
<TableHead className="text-right">Avg hold</TableHead>
              <TableHead className="text-right">Trades</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.map((e) => (
              <TableRow key={e.wallet} className="hover:bg-muted/50 cursor-pointer">
                <TableCell className="text-muted-foreground font-mono text-sm">
                  {e.rank}
                </TableCell>
                <TableCell className="font-mono text-sm">
                  <Link
                    href={`/wallet/${e.wallet}`}
                    className="hover:underline"
                  >
                    {shortWallet(e.wallet)}
                  </Link>
                </TableCell>
                <TableCell className={`text-right font-medium ${e.totalPnl >= 0 ? "text-green-500" : "text-red-500"}`}>
                  {fmtUsd(e.totalPnl)}
                </TableCell>
                <TableCell className={`text-right text-sm ${e.pnl30d >= 0 ? "text-green-500" : "text-red-500"}`}>
                  {fmtUsd(e.pnl30d)}
                </TableCell>
                <TableCell className="text-right text-sm">
                  {fmtPct(e.winRate)}
                </TableCell>
                <TableCell className="text-right text-sm text-orange-500">
                  {fmtPct(e.maxDrawdown)}
                </TableCell>
<TableCell className="text-right text-sm text-muted-foreground">
                  {fmtHold(e.avgHoldMs)}
                </TableCell>
                <TableCell className="text-right text-sm text-muted-foreground">
                  {e.tradeCount}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <p className="text-xs text-muted-foreground mt-4">
        Stats computed from the 10,000 most recent fills per wallet · Updated every 5 minutes
      </p>
    </main>
  );
}
