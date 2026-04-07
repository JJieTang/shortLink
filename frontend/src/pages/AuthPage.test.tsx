import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuthPage } from "@/pages/AuthPage";
import { renderWithAppProviders } from "@/test/testUtils";

const mockedNavigate = vi.fn();
const loginUser = vi.fn();
const registerUser = vi.fn();
const refreshSessionToken = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockedNavigate,
  };
});

vi.mock("@/api/auth", () => ({
  loginUser: (...args: unknown[]) => loginUser(...args),
  registerUser: (...args: unknown[]) => registerUser(...args),
  refreshSessionToken: (...args: unknown[]) => refreshSessionToken(...args),
}));

describe("AuthPage", () => {
  beforeEach(() => {
    mockedNavigate.mockReset();
    loginUser.mockReset();
    registerUser.mockReset();
    refreshSessionToken.mockReset();
    window.localStorage.clear();
  });

  it("logs in and redirects to the links page", async () => {
    const user = userEvent.setup();
    loginUser.mockResolvedValue({
      accessToken: "header.payload.signature",
      refreshToken: "refresh-token",
      tokenType: "Bearer",
    });

    renderWithAppProviders(<AuthPage />);

    await user.type(screen.getByLabelText(/email/i), "owner@example.com");
    await user.type(screen.getByLabelText(/password/i), "Password123");
    await user.click(screen.getByRole("button", { name: /^sign in$/i }));

    await waitFor(() => {
      expect(loginUser).toHaveBeenCalledWith({
        email: "owner@example.com",
        password: "Password123",
      });
    });

    await waitFor(() => {
      expect(mockedNavigate).toHaveBeenCalledWith("/links", { replace: true });
    });

    expect(
      screen.getByText(/signed in successfully/i),
    ).toBeInTheDocument();
  });

  it("keeps the session when refresh fails with a non-api error", async () => {
    const user = userEvent.setup();
    window.localStorage.setItem(
      "shortlink.session",
      JSON.stringify({
        accessToken: "header.payload.signature",
        refreshToken: "refresh-token",
        email: "owner@example.com",
      }),
    );
    refreshSessionToken.mockRejectedValue(new Error("network down"));

    renderWithAppProviders(<AuthPage />);

    await user.click(screen.getByRole("button", { name: /refresh session/i }));

    await waitFor(() => {
      expect(refreshSessionToken).toHaveBeenCalledWith({
        refreshToken: "refresh-token",
      });
    });

    expect(
      JSON.parse(window.localStorage.getItem("shortlink.session") ?? "{}").refreshToken,
    ).toBe("refresh-token");
    expect(screen.getByText(/please try again/i)).toBeInTheDocument();
  });
});
