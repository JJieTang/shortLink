import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  UrlInputForm,
  type UrlInputValues,
} from "@/components/links/UrlInputForm";

const DEFAULT_VALUES: UrlInputValues = {
  originalUrl: "",
  customAlias: "",
  expiresAt: "",
};

describe("UrlInputForm", () => {
  it("emits field changes and submits the form", async () => {
    const user = userEvent.setup();
    let currentValues = { ...DEFAULT_VALUES };
    const handleSubmit = vi.fn((event: React.FormEvent<HTMLFormElement>) =>
      event.preventDefault(),
    );
    const handleChange = vi.fn((field: keyof UrlInputValues, value: string) => {
      currentValues = {
        ...currentValues,
        [field]: value,
      };
      rerender(
        <UrlInputForm
          values={currentValues}
          isSubmitting={false}
          onChange={handleChange}
          onSubmit={handleSubmit}
        />,
      );
    });

    const { rerender } = render(
      <UrlInputForm
        values={currentValues}
        isSubmitting={false}
        onChange={handleChange}
        onSubmit={handleSubmit}
      />,
    );

    await user.type(screen.getByLabelText(/original url/i), "https://example.com");
    await user.type(screen.getByLabelText(/custom alias/i), "launch-day");
    await user.type(screen.getByLabelText(/expires at/i), "2026-04-10T09:30");
    await user.click(screen.getByRole("button", { name: /create short url/i }));

    expect(handleChange).toHaveBeenCalledWith("originalUrl", expect.any(String));
    expect(handleChange).toHaveBeenCalledWith("customAlias", expect.any(String));
    expect(handleChange).toHaveBeenCalledWith("expiresAt", expect.any(String));
    expect(handleSubmit).toHaveBeenCalledOnce();
  });

  it("disables the submit button while submitting", () => {
    render(
      <UrlInputForm
        values={DEFAULT_VALUES}
        isSubmitting
        onChange={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("button", { name: /creating short url/i }),
    ).toBeDisabled();
  });
});
