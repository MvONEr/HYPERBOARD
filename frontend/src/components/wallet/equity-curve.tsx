import type { EquityPoint } from "@/lib/api";
import { fmtUsdSigned } from "@/lib/api";

type Props = {
  points: EquityPoint[];
  width?: number;
  height?: number;
};

export function EquityCurve({ points, width = 800, height = 240 }: Props) {
  if (points.length < 2) {
    return (
      <div
        className="flex items-center justify-center text-sm text-muted-foreground"
        style={{ height }}
      >
        Not enough closed positions to plot an equity curve.
      </div>
    );
  }

  const padX = 8;
  const padY = 12;
  const innerW = width - padX * 2;
  const innerH = height - padY * 2;

  const xs = points.map((p) => p.time);
  const ys = points.map((p) => p.cumPnl);
  const xMin = xs[0];
  const xMax = xs[xs.length - 1];
  const yMin = Math.min(0, ...ys);
  const yMax = Math.max(0, ...ys);
  const yRange = yMax - yMin || 1;
  const xRange = xMax - xMin || 1;

  const px = (t: number) => padX + ((t - xMin) / xRange) * innerW;
  const py = (v: number) => padY + (1 - (v - yMin) / yRange) * innerH;

  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"}${px(p.time).toFixed(2)},${py(p.cumPnl).toFixed(2)}`).join(" ");

  const areaPath = `${linePath} L${px(xMax).toFixed(2)},${py(yMin).toFixed(2)} L${px(xMin).toFixed(2)},${py(yMin).toFixed(2)} Z`;

  const final = ys[ys.length - 1];
  const positive = final >= 0;
  const stroke = positive ? "rgb(34 197 94)" : "rgb(239 68 68)";
  const fill = positive ? "rgba(34, 197, 94, 0.12)" : "rgba(239, 68, 68, 0.12)";
  const zeroY = py(0);

  return (
    <div className="w-full">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="none"
        className="w-full h-60"
      >
        {/* zero line */}
        {yMin < 0 && yMax > 0 && (
          <line
            x1={padX}
            x2={width - padX}
            y1={zeroY}
            y2={zeroY}
            stroke="currentColor"
            strokeOpacity="0.2"
            strokeDasharray="3 3"
          />
        )}
        <path d={areaPath} fill={fill} />
        <path d={linePath} fill="none" stroke={stroke} strokeWidth="1.5" />
      </svg>
      <div className="flex justify-between text-xs text-muted-foreground mt-2 px-2">
        <span>{new Date(xMin).toLocaleDateString()}</span>
        <span className={positive ? "text-green-500" : "text-red-500"}>
          {fmtUsdSigned(final)} cumulative
        </span>
        <span>{new Date(xMax).toLocaleDateString()}</span>
      </div>
    </div>
  );
}
