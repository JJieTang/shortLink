import type { DailyClicks } from "@/types/analytics";

interface AnalyticsTrendChartProps {
  points: DailyClicks[];
}

export function AnalyticsTrendChart({ points }: AnalyticsTrendChartProps) {
  const maxClicks = Math.max(...points.map((point) => point.clicks), 1);

  return (
    <article className="rounded-[32px] border border-ink/10 bg-white px-6 py-6 shadow-sm">
      <p className="text-xs uppercase tracking-[0.24em] text-gold">Trend</p>
      <h3 className="mt-3 text-xl font-semibold text-ink">Clicks by date</h3>

      {points.length > 0 ? (
        <div className="mt-6 grid gap-4">
          {points.map((point) => (
            <div key={point.date} className="grid gap-2">
              <div className="flex items-center justify-between gap-3 text-sm text-ink/65">
                <span>{formatDate(point.date)}</span>
                <span>{point.clicks} clicks</span>
              </div>
              <div className="h-3 overflow-hidden rounded-full bg-mist">
                <div
                  className="h-full rounded-full bg-[linear-gradient(90deg,#d5a741_0%,#c5664a_100%)]"
                  style={{ width: `${Math.max((point.clicks / maxClicks) * 100, 6)}%` }}
                />
              </div>
            </div>
          ))}
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
