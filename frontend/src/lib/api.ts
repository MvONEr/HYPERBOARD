const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type LeaderboardEntry = {
  rank: number;
  wallet: string;
  totalPnl: number;
  pnl30d: number;
  pnl90d: number;
  winRate: number;
  profitFactor: number;
  sharpe: number;
  maxDrawdown: number;
  tradeCount: number;
  avgHoldMs: number;
  tradingStyle: string;
  longPct: number;
};

export type SortField =
  | "total_pnl"
  | "pnl_30d"
  | "pnl_90d"
  | "win_rate"
  | "sharpe"
  | "profit_factor"
  | "max_drawdown"
  | "trade_count";

export async function fetchLeaderboard(
  sort: SortField = "total_pnl",
  style?: string,
  limit = 100
): Promise<LeaderboardEntry[]> {
  const params = new URLSearchParams({ sort, limit: String(limit) });
  if (style) params.set("style", style);
  const res = await fetch(`${API_BASE}/api/leaderboard?${params}`, {
    next: { revalidate: 300 },
  });
  if (!res.ok) throw new Error(`Leaderboard fetch failed: ${res.status}`);
  return res.json();
}

export type WalletStats = {
  wallet: string;
  computedAt: number;
  totalPnl: number;
  pnl30d: number;
  pnl90d: number;
  totalFees: number;
  totalFunding: number;
  winRate: number;
  profitFactor: number;
  sharpe: number;
  maxDrawdown: number;
  tradeCount: number;
  avgHoldMs: number;
  tradingStyle: string;
  avgWin: number;
  avgLoss: number;
  longPct: number;
  accountValue: number | null;
};

export type EquityPoint = { time: number; cumPnl: number };

export type AssetBreakdown = {
  coin: string;
  tradeCount: number;
  realizedPnl: number;
  volume: number;
};

export type RecentFill = {
  tid: number;
  oid: number;
  time: number;
  coin: string;
  dir: string;
  side: "B" | "A";
  px: number;
  sz: number;
  fee: number;
  closedPnl: number;
  startPosition: number;
  crossed: boolean;
  feeToken: string;
  builderFee: number | null;
};

export type OpenPositionView = {
  position: {
    coin: string;
    szi: number;
    entryPx: number;
    positionValue: number;
    unrealizedPnl: number;
    returnOnEquity: number;
    liquidationPx: number | null;
    cumFunding: { allTime: number; sinceOpen: number } | null;
  };
};

export type WalletSummary = {
  wallet: string;
  tracked: boolean;
  stats: WalletStats | null;
  equityCurve: EquityPoint[];
  assetBreakdown: AssetBreakdown[];
  recentFills: RecentFill[];
  fundingEarned: number | null;
  fundingPaid: number | null;
  feesPaid: number | null;
  feesRebated: number | null;
  openPositions: OpenPositionView[];
  accountValue: number | null;
};

export async function fetchWalletSummary(address: string): Promise<WalletSummary> {
  const res = await fetch(`${API_BASE}/api/wallet/${address}/summary`, {
    next: { revalidate: 60 },
  });
  if (!res.ok) throw new Error(`Wallet summary fetch failed: ${res.status}`);
  return res.json();
}

export function fmtUsd(n: number): string {
  if (Math.abs(n) >= 1_000_000)
    return `$${(n / 1_000_000).toFixed(1)}M`;
  if (Math.abs(n) >= 1_000)
    return `$${(n / 1_000).toFixed(1)}K`;
  return `$${n.toFixed(0)}`;
}

export function fmtUsdSigned(n: number): string {
  const sign = n >= 0 ? "+" : "−";
  return `${sign}${fmtUsd(Math.abs(n))}`;
}

export function fmtTime(ms: number): string {
  const d = new Date(ms);
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function fmtRelative(ms: number): string {
  const diff = Date.now() - ms;
  if (diff < 60_000) return `${Math.round(diff / 1000)}s ago`;
  if (diff < 3_600_000) return `${Math.round(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.round(diff / 3_600_000)}h ago`;
  return `${Math.round(diff / 86_400_000)}d ago`;
}

export function fmtPct(n: number): string {
  return `${(n * 100).toFixed(1)}%`;
}

export function fmtHold(ms: number): string {
  if (ms < 60_000) return `${Math.round(ms / 1000)}s`;
  if (ms < 3_600_000) return `${Math.round(ms / 60_000)}m`;
  if (ms < 86_400_000) return `${Math.round(ms / 3_600_000)}h`;
  return `${Math.round(ms / 86_400_000)}d`;
}

export function shortWallet(addr: string): string {
  return `${addr.slice(0, 6)}…${addr.slice(-4)}`;
}
