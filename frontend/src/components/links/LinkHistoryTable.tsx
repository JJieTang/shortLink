import type { PageResponse, ShortUrlRecord } from "@/types/url";

interface LinkHistoryTableProps {
  page: PageResponse<ShortUrlRecord> | null;
  isLoading: boolean;
  deletingShortCode: string | null;
  onPreviousPage: () => void;
  onNextPage: () => void;
  onDelete: (shortCode: string) => void;
}

export function LinkHistoryTable({
  page,
  isLoading,
  deletingShortCode,
  onPreviousPage,
  onNextPage,
  onDelete,
}: LinkHistoryTableProps) {
  return (
    <article className="rounded-[28px] border border-ink/10 bg-white px-6 py-6 shadow-sm">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.24em] text-pine">History</p>
          <h3 className="mt-3 text-xl font-semibold text-ink">Recent short links</h3>
        </div>
        <p className="text-sm text-ink/55">
          {page ? `${page.totalElements} total links` : "Loading your links..."}
        </p>
      </div>

      {isLoading ? (
        <div className="mt-6 rounded-[24px] border border-dashed border-ink/12 bg-mist/50 px-5 py-8 text-sm text-ink/62">
          Loading link history...
        </div>
      ) : page && page.content.length > 0 ? (
        <>
          <div className="mt-6 overflow-x-auto">
            <table className="min-w-full border-separate border-spacing-y-3">
              <thead>
                <tr className="text-left text-xs uppercase tracking-[0.22em] text-ink/40">
                  <th className="pb-1 pr-4 font-medium">Short URL</th>
                  <th className="pb-1 pr-4 font-medium">Destination</th>
                  <th className="pb-1 pr-4 font-medium">Clicks</th>
                  <th className="pb-1 pr-4 font-medium">Created</th>
                  <th className="pb-1 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((url) => (
                  <tr key={url.id} className="rounded-2xl bg-mist/45 text-sm text-ink">
                    <td className="rounded-l-2xl px-4 py-4 align-top">
                      <a
                        href={url.shortUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="font-semibold text-pine underline decoration-pine/25 underline-offset-4"
                      >
                        {url.shortCode}
                      </a>
                    </td>
                    <td className="px-4 py-4 align-top text-ink/68">
                      <span className="block max-w-xs break-all">{url.originalUrl}</span>
                    </td>
                    <td className="px-4 py-4 align-top">{url.totalClicks}</td>
                    <td className="px-4 py-4 align-top text-ink/62">
                      {formatDate(url.createdAt)}
                    </td>
                    <td className="rounded-r-2xl px-4 py-4 align-top">
                      <button
                        type="button"
                        onClick={() => onDelete(url.shortCode)}
                        disabled={deletingShortCode === url.shortCode}
                        className="inline-flex rounded-full border border-ember/15 px-4 py-2 text-sm font-semibold text-ember transition hover:bg-ember/8 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {deletingShortCode === url.shortCode ? "Deleting..." : "Delete"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-ink/8 pt-4">
            <p className="text-sm text-ink/55">
              Page {page.number + 1} of {Math.max(page.totalPages, 1)}
            </p>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={onPreviousPage}
                disabled={page.first}
                className="inline-flex rounded-full border border-ink/10 px-4 py-2 text-sm font-semibold text-ink transition hover:bg-mist disabled:cursor-not-allowed disabled:opacity-60"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={onNextPage}
                disabled={page.last}
                className="inline-flex rounded-full border border-ink/10 px-4 py-2 text-sm font-semibold text-ink transition hover:bg-mist disabled:cursor-not-allowed disabled:opacity-60"
              >
                Next
              </button>
            </div>
          </div>
        </>
      ) : (
        <div className="mt-6 rounded-[24px] border border-dashed border-ink/12 bg-mist/50 px-5 py-8 text-sm text-ink/62">
          You have not created any short links yet. The first successful create request will show up here.
        </div>
      )}
    </article>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
