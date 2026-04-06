import { useState, type FormEvent } from "react";
import { createShortUrl } from "@/api/urls";
import { ShortUrlResultCard } from "@/components/links/ShortUrlResultCard";
import {
  UrlInputForm,
  type UrlInputValues,
} from "@/components/links/UrlInputForm";
import { MissingAccessTokenError, RequestTimeoutError } from "@/api/httpClient";
import { ApiError } from "@/types/api";
import type { ShortUrlRecord } from "@/types/url";

const INITIAL_VALUES: UrlInputValues = {
  originalUrl: "",
  customAlias: "",
  expiresAt: "",
};

export function LinksPage() {
  const [values, setValues] = useState<UrlInputValues>(INITIAL_VALUES);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [createdUrl, setCreatedUrl] = useState<ShortUrlRecord | null>(null);
  const [isCopying, setIsCopying] = useState(false);

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
      setFeedback("Short URL created successfully.");
      setValues((currentValues) => ({
        ...INITIAL_VALUES,
        originalUrl: currentValues.originalUrl.trim(),
      }));
    } catch (error) {
      setFeedback(toMessage(error));
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
      setFeedback("Short URL copied to your clipboard.");
    } catch {
      setFeedback("Copy failed. You can still copy the short URL manually.");
    } finally {
      setIsCopying(false);
    }
  }

  function handleFieldChange(field: keyof UrlInputValues, value: string) {
    setValues((currentValues) => ({
      ...currentValues,
      [field]: value,
    }));
  }

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-pine">Links</p>
        <h2 className="text-2xl font-semibold tracking-tight">Links workspace</h2>
        <p className="max-w-2xl text-sm text-ink/70">
          Create a short URL now, then we will layer the history table on top in the next commit.
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
            <div className="mt-5 rounded-3xl border border-pine/15 bg-pine/8 px-4 py-3 text-sm font-medium text-ink">
              {feedback}
            </div>
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
    </section>
  );
}

function toIsoString(value: string) {
  if (!value) {
    return undefined;
  }

  return new Date(value).toISOString();
}

function toMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  if (error instanceof MissingAccessTokenError) {
    return "Your session is missing. Please sign in again.";
  }

  if (error instanceof RequestTimeoutError) {
    return "The create request took too long. Please try again.";
  }

  return "Create URL failed. Please try again.";
}
