import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AnalyticsTrendChart } from "@/components/analytics/AnalyticsTrendChart";

describe("AnalyticsTrendChart", () => {
  it("shows an empty state when there are no points", () => {
    render(<AnalyticsTrendChart points={[]} />);

    expect(
      screen.getByText(/no daily click data is available for the selected date range/i),
    ).toBeInTheDocument();
  });

  it("renders a recharts bar chart when points are available", () => {
    const { container } = render(
      <AnalyticsTrendChart
        points={[
          { date: "2026-04-01", clicks: 14 },
          { date: "2026-04-02", clicks: 28 },
        ]}
      />,
    );

    expect(screen.getByText(/clicks by date/i)).toBeInTheDocument();
    expect(container.querySelector(".recharts-wrapper")).not.toBeNull();
    expect(container.querySelector(".recharts-cartesian-axis")).not.toBeNull();
  });
});
