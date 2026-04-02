const cards = [
  { title: "Daily clicks", tone: "bg-gold/15 text-gold", body: "Reserved for the clicks-by-date chart from url_daily_stats." },
  { title: "Totals", tone: "bg-ember/15 text-ember", body: "Will surface total and unique clicks from /api/v1/urls/{shortCode}/analytics." },
  { title: "Filters", tone: "bg-pine/15 text-pine", body: "Date controls and loading states will plug into the shared request layer." },
];

export function AnalyticsPage() {
  return (
    <section className="space-y-5">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-gold">Analytics</p>
        <h2 className="text-2xl font-semibold tracking-tight text-ink">Dashboard runway for Phase 4</h2>
        <p className="max-w-2xl text-sm text-ink/70">
          This placeholder page keeps the route live and gives us a home for charts, loading
          states, and empty-state messaging in the next frontend commits.
        </p>
      </header>

      <div className="grid gap-4 lg:grid-cols-3">
        {cards.map((card) => (
          <article key={card.title} className="rounded-[28px] border border-ink/10 bg-white px-5 py-5 shadow-sm">
            <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] ${card.tone}`}>
              {card.title}
            </span>
            <p className="mt-4 text-sm leading-6 text-ink/70">{card.body}</p>
          </article>
        ))}
      </div>

      <div className="rounded-[32px] border border-dashed border-ink/15 bg-mist/70 px-6 py-10 text-center">
        <p className="text-xs uppercase tracking-[0.28em] text-ink/45">Upcoming chart surface</p>
        <h3 className="mt-3 text-xl font-semibold text-ink">Recharts integration lands after the shell commit</h3>
        <p className="mx-auto mt-3 max-w-xl text-sm text-ink/62">
          The route, page frame, and visual language are already in place so the analytics widgets can
          be added without revisiting the app-level wiring.
        </p>
      </div>
    </section>
  );
}
