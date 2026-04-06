export function LinksPage() {
  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-pine">Links</p>
        <h2 className="text-2xl font-semibold tracking-tight">Links workspace</h2>
        <p className="max-w-2xl text-sm text-ink/70">
          The create-link form and link history table land here next.
        </p>
      </header>

      <article className="rounded-[28px] border border-dashed border-ink/15 bg-white/80 px-6 py-10 shadow-sm">
        <p className="text-xs uppercase tracking-[0.24em] text-pine">Placeholder</p>
        <h3 className="mt-3 text-xl font-semibold text-ink">Create and manage links</h3>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-ink/68">
          This route stays live while we add the short-link form, result card, and history list.
        </p>
      </article>
    </section>
  );
}
