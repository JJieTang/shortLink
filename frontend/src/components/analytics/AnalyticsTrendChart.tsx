import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { DailyClicks } from "@/types/analytics";

interface AnalyticsTrendChartProps {
  points: DailyClicks[];
}

export function AnalyticsTrendChart({ points }: AnalyticsTrendChartProps) {
  const chartWidth = Math.max(points.length * 88, 520);
  const chartData = points.map((point) => ({
    ...point,
    label: formatDate(point.date),
  }));

  return (
    <article className="rounded-[32px] border border-ink/10 bg-white px-6 py-6 shadow-sm">
      <p className="text-xs uppercase tracking-[0.24em] text-gold">Trend</p>
      <h3 className="mt-3 text-xl font-semibold text-ink">Clicks by date</h3>

      {points.length > 0 ? (
        <div className="mt-6 overflow-x-auto rounded-[24px] border border-ink/8 bg-mist/35 px-3 py-4">
          <BarChart
            width={chartWidth}
            height={300}
            data={chartData}
            margin={{ top: 16, right: 16, bottom: 8, left: 0 }}
          >
            <defs>
              <linearGradient id="analyticsTrendFill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stopColor="#d5a741" stopOpacity={0.95} />
                <stop offset="100%" stopColor="#c5664a" stopOpacity={0.85} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#d8e0db" strokeDasharray="4 6" vertical={false} />
            <XAxis
              axisLine={false}
              dataKey="label"
              tickLine={false}
              tick={{ fill: "#5b645d", fontSize: 12 }}
            />
            <YAxis
              allowDecimals={false}
              axisLine={false}
              tickLine={false}
              tick={{ fill: "#5b645d", fontSize: 12 }}
            />
            <Tooltip
              cursor={{ fill: "rgba(213, 167, 65, 0.08)" }}
              contentStyle={{
                borderRadius: "16px",
                border: "1px solid rgba(17, 24, 39, 0.08)",
                boxShadow: "0 18px 44px rgba(26, 36, 31, 0.12)",
              }}
              formatter={(value) => [`${Number(value ?? 0)} clicks`, "Clicks"]}
              labelStyle={{ color: "#1f2a24", fontWeight: 600 }}
            />
            <Bar
              dataKey="clicks"
              fill="url(#analyticsTrendFill)"
              name="Clicks"
              radius={[10, 10, 4, 4]}
            >
              {chartData.map((point) => (
                <Cell key={point.date} fill="url(#analyticsTrendFill)" />
              ))}
            </Bar>
          </BarChart>
        </div>
      ) : (
        <div className="mt-6 rounded-[24px] border border-dashed border-ink/12 bg-mist/50 px-5 py-8 text-sm text-ink/62">
          No daily click data is available for the selected date range.
        </div>
      )}
    </article>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
  }).format(new Date(value));
}
