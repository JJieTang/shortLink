import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AnalyticsPage } from "@/pages/AnalyticsPage";
import { renderWithAppProviders } from "@/test/testUtils";

const listShortUrls = vi.fn();
const getUrlAnalytics = vi.fn();

vi.mock("@/api/urls", async () => {
  const actual = await vi.importActual<typeof import("@/api/urls")>("@/api/urls");
  return {
    ...actual,
    listShortUrls: (...args: unknown[]) => listShortUrls(...args),
  };
});

vi.mock("@/api/analytics", () => ({
  getUrlAnalytics: (...args: unknown[]) => getUrlAnalytics(...args),
}));

describe("AnalyticsPage", () => {
  beforeEach(() => {
    listShortUrls.mockReset();
    getUrlAnalytics.mockReset();
    window.localStorage.clear();
    window.localStorage.setItem(
      "shortlink.session",
      JSON.stringify({
        accessToken:
          "eyJhbGciOiJIUzI1NiJ9.eyJleHAiIjo0MTAyNDQ0ODAwfQ.signature",
        refreshToken: "refresh-token",
        email: "owner@example.com",
      }),
    );
  });

  it("loads short codes and displays analytics summary cards", async () => {
    listShortUrls.mockResolvedValue({
      content: [{ shortCode: "report-link" }],
    });
    getUrlAnalytics.mockResolvedValue({
      shortCode: "report-link",
      totalClicks: 1542,
      periodClicks: 229,
      uniqueClicks: 163,
      clicksByDate: [
        { date: "2026-03-24", clicks: 87 },
        { date: "2026-03-25", clicks: 142 },
      ],
    });

    renderWithAppProviders(<AnalyticsPage />);

    await waitFor(() => {
      expect(listShortUrls).toHaveBeenCalledWith(0, 20);
    });

    await waitFor(() => {
      expect(getUrlAnalytics).toHaveBeenCalled();
    });

    expect(screen.getByText("1542")).toBeInTheDocument();
    expect(screen.getByText("229")).toBeInTheDocument();
    expect(screen.getByText("163")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "report-link" })).toBeInTheDocument();
    expect(screen.getByText(/clicks by date/i)).toBeInTheDocument();
  });

  it("shows an empty-state message when there are no short links", async () => {
    listShortUrls.mockResolvedValue({ content: [] });

    renderWithAppProviders(<AnalyticsPage />);

    await waitFor(() => {
      expect(listShortUrls).toHaveBeenCalled();
    });

    expect(screen.getByText(/create a short link first/i)).toBeInTheDocument();
    expect(getUrlAnalytics).not.toHaveBeenCalled();
  });

  it("reloads analytics when the user submits new filters", async () => {
    const user = userEvent.setup();
    listShortUrls.mockResolvedValue({
      content: [{ shortCode: "report-link" }, { shortCode: "spring-link" }],
    });
    getUrlAnalytics
      .mockResolvedValueOnce({
        shortCode: "report-link",
        totalClicks: 20,
        periodClicks: 10,
        uniqueClicks: 5,
        clicksByDate: [],
      })
      .mockResolvedValueOnce({
        shortCode: "spring-link",
        totalClicks: 40,
        periodClicks: 18,
        uniqueClicks: 11,
        clicksByDate: [],
      });

    renderWithAppProviders(<AnalyticsPage />);

    await waitFor(() => {
      expect(getUrlAnalytics).toHaveBeenCalledTimes(1);
    });

    await user.selectOptions(screen.getByLabelText(/short code/i), "spring-link");
    await user.click(screen.getByRole("button", { name: /load analytics/i }));

    await waitFor(() => {
      expect(getUrlAnalytics).toHaveBeenCalledTimes(2);
    });

    expect(getUrlAnalytics).toHaveBeenLastCalledWith(
      "spring-link",
      expect.any(String),
      expect.any(String),
    );
  });
});
