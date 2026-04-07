import { useEffect, useState, type FormEvent } from "react";
import { createShortUrl, deleteShortUrl, listShortUrls } from "@/api/urls";
import { FeedbackBanner } from "@/components/FeedbackBanner";
import { LinkHistoryTable } from "@/components/links/LinkHistoryTable";
import { ShortUrlResultCard } from "@/components/links/ShortUrlResultCard";
import {
  UrlInputForm,
  type UrlInputValues,
} from "@/components/links/UrlInputForm";
import type { FeedbackState } from "@/types/feedback";
import type { PageResponse, ShortUrlRecord } from "@/types/url";
import { toFeedbackErrorMessage } from "@/utils/errorMessage";

const INITIAL_VALUES: UrlInputValues = {
  originalUrl: "",
  customAlias: "",
  expiresAt: "",
};
const PAGE_SIZE = 5;

export function LinksPage() {
  const [values, setValues] = useState<UrlInputValues>(INITIAL_VALUES);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [createdUrl, setCreatedUrl] = useState<ShortUrlRecord | null>(null);
  const [isCopying, setIsCopying] = useState(false);
  const [historyPage, setHistoryPage] = useState<PageResponse<ShortUrlRecord> | null>(null);
  const [historyPageIndex, setHistoryPageIndex] = useState(0);
  const [isHistoryLoading, setIsHistoryLoading] = useState(true);
  const [deletingShortCode, setDeletingShortCode] = useState<string | null>(null);

  useEffect(() => {
    void loadHistoryPage(historyPageIndex);
  }, [historyPageIndex]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setFeedback(null);

    try {
      const created = await createShortUrl({
        originalUrl: values.originalUrl.trim(),
        customAlias: values.customAlias.trim() || undefined,
        expiresAt: toIsoString(values.expiresAt),
      });

      setCreatedUrl(created);
      setFeedback({
        tone: "success",
        message: "Short URL created successfully.",
      });
      setValues((currentValues) => ({
        ...INITIAL_VALUES,
        originalUrl: currentValues.originalUrl.trim(),
      }));

      if (historyPageIndex === 0) {
        await loadHistoryPage(0);
      } else {
        setHistoryPageIndex(0);
      }
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toFeedbackErrorMessage(error, {
          fallback: "Links request failed. Please try again.",
          timeoutMessage: "The links request took too long. Please try again.",
        }),
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCopy() {
    if (!createdUrl) {
      return;
    }

    setIsCopying(true);

    try {
      await navigator.clipboard.writeText(createdUrl.shortUrl);
      setFeedback({
        tone: "info",
        message: "Short URL copied to your clipboard.",
      });
    } catch {
      setFeedback({
        tone: "error",
        message: "Copy failed. You can still copy the short URL manually.",
      });
    } finally {
      setIsCopying(false);
    }
  }

  async function handleDelete(shortCode: string) {
    setDeletingShortCode(shortCode);
    setFeedback(null);

    try {
      await deleteShortUrl(shortCode);

      if (createdUrl?.shortCode === shortCode) {
        setCreatedUrl(null);
      }

      const nextPageIndex =
        historyPage && historyPage.content.length === 1 && historyPageIndex > 0
          ? historyPageIndex - 1
          : historyPageIndex;

      if (nextPageIndex === historyPageIndex) {
        await loadHistoryPage(nextPageIndex);
      } else {
        setHistoryPageIndex(nextPageIndex);
      }

      setFeedback({
        tone: "success",
        message: `Deleted short link ${shortCode}.`,
      });
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toFeedbackErrorMessage(error, {
          fallback: "Links request failed. Please try again.",
          timeoutMessage: "The links request took too long. Please try again.",
        }),
      });
    } finally {
      setDeletingShortCode(null);
    }
  }

  function handleFieldChange(field: keyof UrlInputValues, value: string) {
    setValues((currentValues) => ({
      ...currentValues,
      [field]: value,
    }));
  }

  async function loadHistoryPage(pageIndex: number) {
    setIsHistoryLoading(true);

    try {
      const nextPage = await listShortUrls(pageIndex, PAGE_SIZE);
      setHistoryPage(nextPage);
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toFeedbackErrorMessage(error, {
          fallback: "Links request failed. Please try again.",
          timeoutMessage: "The links request took too long. Please try again.",
        }),
      });
    } finally {
      setIsHistoryLoading(false);
    }
  }

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-pine">Links</p>
        <h2 className="text-2xl font-semibold tracking-tight">Links workspace</h2>
        <p className="max-w-2xl text-sm text-ink/70">
          Create new short links, review the newest result, and manage your recent history from one workspace.
        </p>
      </header>

      <div className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
        <article className="rounded-[28px] border border-ink/10 bg-white px-6 py-6 shadow-sm">
          <p className="text-xs uppercase tracking-[0.24em] text-pine">Create link</p>
          <h3 className="mt-3 text-xl font-semibold text-ink">Turn a long URL into a short code</h3>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-ink/68">
            You can paste a destination URL, add an optional custom alias, and choose an optional expiry date.
          </p>

          {feedback ? (
            <FeedbackBanner
              tone={feedback.tone}
              message={feedback.message}
              className="mt-5"
            />
          ) : null}

          <div className="mt-6">
            <UrlInputForm
              values={values}
              isSubmitting={isSubmitting}
              onChange={handleFieldChange}
              onSubmit={handleSubmit}
            />
          </div>
        </article>

        {createdUrl ? (
          <ShortUrlResultCard
            url={createdUrl}
            isCopying={isCopying}
            onCopy={handleCopy}
          />
        ) : (
          <article className="rounded-[28px] border border-dashed border-ink/15 bg-white/80 px-6 py-10 shadow-sm">
            <p className="text-xs uppercase tracking-[0.24em] text-pine">Result card</p>
            <h3 className="mt-3 text-xl font-semibold text-ink">Your newest short link will appear here</h3>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-ink/68">
              After a successful create request, we will show the short URL, expiry state, and copy action in this panel.
            </p>
          </article>
        )}
      </div>

      <LinkHistoryTable
        page={historyPage}
        isLoading={isHistoryLoading}
        deletingShortCode={deletingShortCode}
        onPreviousPage={() => setHistoryPageIndex((pageIndex) => Math.max(pageIndex - 1, 0))}
        onNextPage={() => setHistoryPageIndex((pageIndex) => pageIndex + 1)}
        onDelete={handleDelete}
      />
    </section>
  );
}

function toIsoString(value: string) {
  if (!value) {
    return undefined;
  }

  return new Date(value).toISOString();
}
