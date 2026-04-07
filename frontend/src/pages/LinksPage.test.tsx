import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LinksPage } from "@/pages/LinksPage";
import { renderWithAppProviders } from "@/test/testUtils";

const createShortUrl = vi.fn();
const deleteShortUrl = vi.fn();
const listShortUrls = vi.fn();

vi.mock("@/api/urls", async () => {
  const actual = await vi.importActual<typeof import("@/api/urls")>("@/api/urls");
  return {
    ...actual,
    createShortUrl: (...args: unknown[]) => createShortUrl(...args),
    deleteShortUrl: (...args: unknown[]) => deleteShortUrl(...args),
    listShortUrls: (...args: unknown[]) => listShortUrls(...args),
  };
});

describe("LinksPage", () => {
  beforeEach(() => {
    createShortUrl.mockReset();
    deleteShortUrl.mockReset();
    listShortUrls.mockReset();
    listShortUrls.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 5,
      first: true,
      last: true,
    });

    Object.defineProperty(window.navigator, "clipboard", {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    });
  });

  it("creates a short url and shows the newest result card", async () => {
    const user = userEvent.setup();
    createShortUrl.mockResolvedValue({
      id: "url-1",
      shortCode: "launch-2026",
      shortUrl: "https://sho.rt/launch-2026",
      originalUrl: "https://example.com/launch",
      totalClicks: 0,
      expiresAt: null,
      createdAt: "2026-04-07T09:00:00.000Z",
      updatedAt: "2026-04-07T09:00:00.000Z",
    });

    renderWithAppProviders(<LinksPage />);

    await user.type(screen.getByLabelText(/original url/i), "  https://example.com/launch  ");
    await user.click(screen.getByRole("button", { name: /create short url/i }));

    await waitFor(() => {
      expect(createShortUrl).toHaveBeenCalledWith({
        originalUrl: "https://example.com/launch",
        customAlias: undefined,
        expiresAt: undefined,
      });
    });

    expect(screen.getByText(/short url created successfully/i)).toBeInTheDocument();
    expect(screen.getByText(/short link is ready/i)).toBeInTheDocument();
    expect(screen.getByText("https://sho.rt/launch-2026")).toBeInTheDocument();
  });

  it("copies the newest short url and shows feedback", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);

    Object.defineProperty(window.navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });

    createShortUrl.mockResolvedValue({
      id: "url-2",
      shortCode: "spring-launch",
      shortUrl: "https://sho.rt/spring-launch",
      originalUrl: "https://example.com/spring-launch",
      totalClicks: 12,
      expiresAt: null,
      createdAt: "2026-04-07T09:00:00.000Z",
      updatedAt: "2026-04-07T09:00:00.000Z",
    });

    renderWithAppProviders(<LinksPage />);

    await user.type(screen.getByLabelText(/original url/i), "https://example.com/spring-launch");
    await user.click(screen.getByRole("button", { name: /create short url/i }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /copy short url/i })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /copy short url/i }));

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith("https://sho.rt/spring-launch");
    });

    expect(screen.getByText(/short url copied to your clipboard/i)).toBeInTheDocument();
  });
});
