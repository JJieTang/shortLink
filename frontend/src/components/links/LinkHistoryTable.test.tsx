import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LinkHistoryTable } from "@/components/links/LinkHistoryTable";
import type { PageResponse, ShortUrlRecord } from "@/types/url";

function buildPage(content: ShortUrlRecord[]): PageResponse<ShortUrlRecord> {
  return {
    content,
    totalElements: content.length,
    totalPages: 2,
    number: 0,
    size: 5,
    first: true,
    last: false,
  };
}

const SAMPLE_URL: ShortUrlRecord = {
  id: "1",
  shortCode: "launch-day",
  shortUrl: "http://localhost:8080/launch-day",
  originalUrl: "https://example.com/launch",
  totalClicks: 42,
  expiresAt: null,
  createdAt: "2026-04-07T08:00:00Z",
  updatedAt: "2026-04-07T08:00:00Z",
};

describe("LinkHistoryTable", () => {
  it("renders the empty state when there is no history", () => {
    render(
      <LinkHistoryTable
        page={buildPage([])}
        isLoading={false}
        deletingShortCode={null}
        onPreviousPage={vi.fn()}
        onNextPage={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(
      screen.getByText(/you have not created any short links yet/i),
    ).toBeInTheDocument();
  });

  it("renders rows and forwards pagination and delete actions", async () => {
    const user = userEvent.setup();
    const handlePrevious = vi.fn();
    const handleNext = vi.fn();
    const handleDelete = vi.fn();

    render(
      <LinkHistoryTable
        page={buildPage([SAMPLE_URL])}
        isLoading={false}
        deletingShortCode={null}
        onPreviousPage={handlePrevious}
        onNextPage={handleNext}
        onDelete={handleDelete}
      />,
    );

    expect(screen.getByText("launch-day")).toBeInTheDocument();
    expect(screen.getByText("https://example.com/launch")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /next/i }));
    await user.click(screen.getByRole("button", { name: /delete/i }));

    expect(handleNext).toHaveBeenCalledOnce();
    expect(handleDelete).toHaveBeenCalledWith("launch-day");
    expect(screen.getByRole("button", { name: /previous/i })).toBeDisabled();
  });
});
