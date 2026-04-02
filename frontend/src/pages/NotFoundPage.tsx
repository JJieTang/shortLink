import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <section className="flex min-h-[50vh] items-center justify-center">
      <div className="max-w-lg rounded-[32px] border border-ink/10 bg-white px-8 py-10 text-center shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-[0.28em] text-ember">404</p>
        <h2 className="mt-4 text-3xl font-semibold tracking-tight text-ink">That route is not wired yet</h2>
        <p className="mt-3 text-sm leading-6 text-ink/68">
          The frontend shell is in place, but this page is not part of the first navigation set.
        </p>
        <Link
          to="/links"
          className="mt-6 inline-flex rounded-full bg-ink px-5 py-3 text-sm font-semibold text-white transition hover:bg-pine"
        >
          Back to links
        </Link>
      </div>
    </section>
  );
}
