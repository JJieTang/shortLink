const pipelineSteps = [
  "Collect long URL input and optional alias/expiry.",
  "Send authenticated create requests through the shared API client.",
  "Render result cards, copy actions, and list state transitions.",
];

export function LinksPage() {
  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-pine">Links</p>
        <h2 className="text-2xl font-semibold tracking-tight">Command deck for the main user flow</h2>
        <p className="max-w-3xl text-sm text-ink/70">
          This view anchors the eventual input form, result panel, and link history table.
          For the first frontend commit, it mainly proves the route tree, visual shell, and
          page-level composition.
        </p>
      </header>

      <div className="grid gap-5 xl:grid-cols-[1.35fr_0.95fr]">
        <article className="rounded-[28px] border border-ink/10 bg-[linear-gradient(135deg,#18303a_0%,#37514a_100%)] px-6 py-6 text-white shadow-sm">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.24em] text-white/58">App shell milestone</p>
              <h3 className="mt-3 text-2xl font-semibold">Shared structure is ready for the main CTA flow</h3>
            </div>
            <span className="rounded-full border border-white/20 px-3 py-1 text-xs uppercase tracking-[0.24em] text-white/70">
              scaffold
            </span>
          </div>

          <ol className="mt-8 grid gap-3">
            {pipelineSteps.map((step, index) => (
              <li key={step} className="flex gap-3 rounded-2xl bg-white/8 px-4 py-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-white/14 text-sm font-semibold">
                  0{index + 1}
                </span>
                <p className="text-sm text-white/78">{step}</p>
              </li>
            ))}
          </ol>
        </article>

        <aside className="grid gap-4">
          <StatusCard
            label="API client"
            title="Ready"
            body="Shared GET/POST/DELETE helpers already resolve the base URL and auth header."
          />
          <StatusCard
            label="Session storage"
            title="Ready"
            body="The app can persist tokens in localStorage for upcoming auth flows."
          />
          <StatusCard
            label="List + form pages"
            title="Next"
            body="CreateUrl form, history table, and deletion UX can now build on top of this shell."
          />
        </aside>
      </div>
    </section>
  );
}

function StatusCard(props: { label: string; title: string; body: string }) {
  return (
    <article className="rounded-[28px] border border-ink/10 bg-white px-5 py-5 shadow-sm">
      <p className="text-xs uppercase tracking-[0.24em] text-pine">{props.label}</p>
      <h3 className="mt-3 text-lg font-semibold text-ink">{props.title}</h3>
      <p className="mt-2 text-sm leading-6 text-ink/68">{props.body}</p>
    </article>
  );
}
