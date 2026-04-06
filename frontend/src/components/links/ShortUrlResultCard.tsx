import type { ShortUrlRecord } from "@/types/url";

interface ShortUrlResultCardProps {
  url: ShortUrlRecord;
  isCopying: boolean;
  onCopy: () => void;
}

export function ShortUrlResultCard({
  url,
  isCopying,
  onCopy,
}: ShortUrlResultCardProps) {
  return (
    <article className="rounded-[28px] border border-ink/10 bg-[linear-gradient(135deg,#18303a_0%,#37514a_100%)] px-6 py-6 text-white shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.24em] text-white/58">Latest result</p>
          <h3 className="mt-3 text-2xl font-semibold">Short link is ready</h3>
        </div>
        <span className="rounded-full border border-white/20 px-3 py-1 text-xs uppercase tracking-[0.24em] text-white/70">
          {url.shortCode}
        </span>
      </div>

      <div className="mt-6 rounded-[24px] bg-white/10 p-4">
        <p className="text-xs uppercase tracking-[0.24em] text-white/58">Short URL</p>
        <a
          href={url.shortUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-3 block break-all text-lg font-semibold text-white underline decoration-white/30 underline-offset-4"
        >
          {url.shortUrl}
        </a>
        <p className="mt-3 text-sm leading-6 text-white/72">
          Redirects to {url.originalUrl}
        </p>
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        <MetaCard label="Clicks" value={String(url.totalClicks)} />
        <MetaCard label="Expires" value={formatDate(url.expiresAt) ?? "No expiry"} />
      </div>

      <div className="mt-6 flex flex-wrap gap-3">
        <button
          type="button"
          onClick={onCopy}
          disabled={isCopying}
          className="inline-flex items-center justify-center rounded-full bg-white px-5 py-3 text-sm font-semibold text-ink transition hover:bg-mist disabled:cursor-not-allowed disabled:bg-white/50"
        >
          {isCopying ? "Copying..." : "Copy short URL"}
        </button>
        <a
          href={url.shortUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center justify-center rounded-full border border-white/20 px-5 py-3 text-sm font-semibold text-white transition hover:bg-white/10"
        >
          Open short URL
        </a>
      </div>
    </article>
  );
}

function MetaCard(props: { label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-white/8 px-4 py-4">
      <p className="text-[11px] uppercase tracking-[0.24em] text-white/58">{props.label}</p>
      <p className="mt-3 text-base font-semibold text-white">{props.value}</p>
    </div>
  );
}

function formatDate(value: string | null) {
  if (!value) {
    return null;
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
