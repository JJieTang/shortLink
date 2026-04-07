import { useEffect, useState } from "react";
import { getUrlAnalytics } from "@/api/analytics";
import { listShortUrls } from "@/api/urls";
import { AnalyticsFilters } from "@/components/analytics/AnalyticsFilters";
import { AnalyticsSummaryCards } from "@/components/analytics/AnalyticsSummaryCards";
import { AnalyticsTrendChart } from "@/components/analytics/AnalyticsTrendChart";
import { FeedbackBanner } from "@/components/FeedbackBanner";
import type { UrlAnalytics } from "@/types/analytics";
import type { FeedbackState } from "@/types/feedback";
import { toFeedbackErrorMessage } from "@/utils/errorMessage";

export function AnalyticsPage() {
  const [availableShortCodes, setAvailableShortCodes] = useState<string[]>([]);
  const [selectedShortCode, setSelectedShortCode] = useState("");
  const [fromDate, setFromDate] = useState(defaultFromDate());
  const [toDate, setToDate] = useState(defaultToDate());
  const [analytics, setAnalytics] = useState<UrlAnalytics | null>(null);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void loadAvailableLinks(fromDate, toDate);
  }, []);

  async function loadAvailableLinks(initialFromDate: string, initialToDate: string) {
    setIsLoading(true);
    setFeedback(null);

    try {
      const shortCodes = await loadRecentShortCodes();
      setAvailableShortCodes(shortCodes);

      if (shortCodes.length === 0) {
        setSelectedShortCode("");
        setAnalytics(null);
        return;
      }

      const initialShortCode = shortCodes[0];
      setSelectedShortCode(initialShortCode);
      const response = await safeAnalyticsRequest(
        initialShortCode,
        initialFromDate,
        initialToDate,
      );
      setAnalytics(response);
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toFeedbackErrorMessage(error, {
          fallback: "Analytics request failed. Please try again.",
          timeoutMessage: "The analytics request took too long. Please try again.",
        }),
      });
    } finally {
      setIsLoading(false);
    }
  }

  async function loadAnalytics(shortCode: string, from: string, to: string) {
    if (!shortCode) {
      setFeedback({
        tone: "info",
        message: "Choose a short code before loading analytics.",
      });
      return;
    }

    setIsLoading(true);
    setFeedback(null);

    try {
      const response = await safeAnalyticsRequest(shortCode, from, to);
      setAnalytics(response);
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toFeedbackErrorMessage(error, {
          fallback: "Analytics request failed. Please try again.",
          timeoutMessage: "The analytics request took too long. Please try again.",
        }),
      });
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <section className="space-y-5">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-gold">Analytics</p>
        <h2 className="text-2xl font-semibold tracking-tight text-ink">Analytics dashboard</h2>
        <p className="max-w-2xl text-sm text-ink/70">
          Choose one of your short links, set a date range, and review click totals plus the daily trend.
        </p>
      </header>

      {feedback ? <FeedbackBanner tone={feedback.tone} message={feedback.message} /> : null}

      <AnalyticsFilters
        availableShortCodes={availableShortCodes}
        selectedShortCode={selectedShortCode}
        fromDate={fromDate}
        toDate={toDate}
        isLoading={isLoading}
        onShortCodeChange={setSelectedShortCode}
        onFromDateChange={setFromDate}
        onToDateChange={setToDate}
        onSubmit={() => void loadAnalytics(selectedShortCode, fromDate, toDate)}
      />

      {analytics ? (
        <>
          <AnalyticsSummaryCards analytics={analytics} />
          <AnalyticsTrendChart points={analytics.clicksByDate} />
        </>
      ) : (
        <article className="rounded-[32px] border border-dashed border-ink/15 bg-mist/70 px-6 py-10 text-center">
          <p className="text-xs uppercase tracking-[0.28em] text-ink/45">Empty state</p>
          <h3 className="mt-3 text-xl font-semibold text-ink">
            {availableShortCodes.length > 0
              ? "Load analytics to see your chart"
              : "Create a short link first"}
          </h3>
          <p className="mx-auto mt-3 max-w-xl text-sm text-ink/62">
            {availableShortCodes.length > 0
              ? "We already loaded your recent short codes. Pick one and fetch the report for your chosen dates."
              : "The analytics dashboard needs at least one managed short link before it can show totals and trends."}
          </p>
        </article>
      )}
    </section>
  );
}

function defaultFromDate() {
  const value = new Date();
  value.setDate(value.getDate() - 6);
  return value.toISOString().slice(0, 10);
}

function defaultToDate() {
  return new Date().toISOString().slice(0, 10);
}

async function loadRecentShortCodes() {
  const response = await listShortUrls(0, 20);
  return response.content.map((url) => url.shortCode);
}

async function safeAnalyticsRequest(
  shortCode: string,
  fromDate: string,
  toDate: string,
) {
  return getUrlAnalytics(shortCode, fromDate, toDate);
}
