interface AnalyticsFiltersProps {
  availableShortCodes: string[];
  selectedShortCode: string;
  fromDate: string;
  toDate: string;
  isLoading: boolean;
  onShortCodeChange: (value: string) => void;
  onFromDateChange: (value: string) => void;
  onToDateChange: (value: string) => void;
  onSubmit: () => void;
}

export function AnalyticsFilters({
  availableShortCodes,
  selectedShortCode,
  fromDate,
  toDate,
  isLoading,
  onShortCodeChange,
  onFromDateChange,
  onToDateChange,
  onSubmit,
}: AnalyticsFiltersProps) {
  return (
    <article className="rounded-[28px] border border-ink/10 bg-white px-6 py-6 shadow-sm">
      <p className="text-xs uppercase tracking-[0.24em] text-gold">Filters</p>
      <h3 className="mt-3 text-xl font-semibold text-ink">Choose a link and date range</h3>

      <div className="mt-6 grid gap-4 md:grid-cols-[1.2fr_0.8fr_0.8fr]">
        <Field label="Short code" htmlFor="analytics-short-code">
          <select
            id="analytics-short-code"
            value={selectedShortCode}
            onChange={(event) => onShortCodeChange(event.target.value)}
            className={inputClassName}
            disabled={availableShortCodes.length === 0}
          >
            {availableShortCodes.length === 0 ? (
              <option value="">No links yet</option>
            ) : (
              availableShortCodes.map((shortCode) => (
                <option key={shortCode} value={shortCode}>
                  {shortCode}
                </option>
              ))
            )}
          </select>
        </Field>

        <Field label="From" htmlFor="analytics-from">
          <input
            id="analytics-from"
            type="date"
            value={fromDate}
            onChange={(event) => onFromDateChange(event.target.value)}
            className={inputClassName}
          />
        </Field>

        <Field label="To" htmlFor="analytics-to">
          <input
            id="analytics-to"
            type="date"
            value={toDate}
            onChange={(event) => onToDateChange(event.target.value)}
            className={inputClassName}
          />
        </Field>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={onSubmit}
          disabled={isLoading || !selectedShortCode}
          className={primaryButtonClassName}
        >
          {isLoading ? "Loading analytics..." : "Load analytics"}
        </button>
        <p className="text-sm text-ink/55">
          Analytics are fetched from the protected `/api/v1/urls/{'{shortCode}'}/analytics` route.
        </p>
      </div>
    </article>
  );
}

function Field(props: {
  label: string;
  htmlFor: string;
  children: React.ReactNode;
}) {
  return (
    <label className="grid gap-2" htmlFor={props.htmlFor}>
      <span className="text-sm font-semibold text-ink">{props.label}</span>
      {props.children}
    </label>
  );
}

const inputClassName =
  "w-full rounded-2xl border border-ink/12 bg-mist/60 px-4 py-3 text-sm text-ink outline-none transition focus:border-pine/40 focus:bg-white";

const primaryButtonClassName =
  "inline-flex items-center justify-center rounded-full bg-ink px-5 py-3 text-sm font-semibold text-white transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40";
