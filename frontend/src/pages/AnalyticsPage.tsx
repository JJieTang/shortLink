export function AnalyticsPage() {
  return (
    <section className="space-y-5">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-gold">Analytics</p>
        <h2 className="text-2xl font-semibold tracking-tight text-ink">Analytics dashboard</h2>
        <p className="max-w-2xl text-sm text-ink/70">
          Charts, totals, and date filters will appear here next.
        </p>
      </header>

      <article className="rounded-[32px] border border-dashed border-ink/15 bg-mist/70 px-6 py-10 text-center">
        <p className="text-xs uppercase tracking-[0.28em] text-ink/45">Placeholder</p>
        <h3 className="mt-3 text-xl font-semibold text-ink">Traffic insights coming soon</h3>
        <p className="mx-auto mt-3 max-w-xl text-sm text-ink/62">
          This route stays ready for click charts, summary metrics, and empty states.
        </p>
      </article>
    </section>
  );
}
