import type { UrlAnalytics } from "@/types/analytics";

interface AnalyticsSummaryCardsProps {
  analytics: UrlAnalytics;
}

export function AnalyticsSummaryCards({
  analytics,
}: AnalyticsSummaryCardsProps) {
  const cards = [
    { label: "Short code", value: analytics.shortCode, tone: "text-gold" },
    { label: "Total clicks", value: String(analytics.totalClicks), tone: "text-ink" },
    { label: "Period clicks", value: String(analytics.periodClicks), tone: "text-pine" },
    { label: "Unique clicks", value: String(analytics.uniqueClicks), tone: "text-ember" },
  ];

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {cards.map((card) => (
        <article key={card.label} className="rounded-[28px] border border-ink/10 bg-white px-5 py-5 shadow-sm">
          <p className="text-xs uppercase tracking-[0.24em] text-ink/40">{card.label}</p>
          <p className={`mt-4 text-2xl font-semibold ${card.tone}`}>{card.value}</p>
        </article>
      ))}
    </div>
  );
}
