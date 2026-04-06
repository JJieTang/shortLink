import type { FormEvent } from "react";

export interface UrlInputValues {
  originalUrl: string;
  customAlias: string;
  expiresAt: string;
}

interface UrlInputFormProps {
  values: UrlInputValues;
  isSubmitting: boolean;
  onChange: (field: keyof UrlInputValues, value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

export function UrlInputForm({
  values,
  isSubmitting,
  onChange,
  onSubmit,
}: UrlInputFormProps) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <Field label="Original URL" htmlFor="original-url">
        <input
          id="original-url"
          type="url"
          required
          value={values.originalUrl}
          onChange={(event) => onChange("originalUrl", event.target.value)}
          className={inputClassName}
          placeholder="https://example.com/article"
        />
      </Field>

      <div className="grid gap-4 md:grid-cols-2">
        <Field label="Custom alias" htmlFor="custom-alias" hint="Optional">
          <input
            id="custom-alias"
            type="text"
            maxLength={30}
            value={values.customAlias}
            onChange={(event) => onChange("customAlias", event.target.value)}
            className={inputClassName}
            placeholder="spring-launch"
          />
        </Field>

        <Field label="Expires at" htmlFor="expires-at" hint="Optional">
          <input
            id="expires-at"
            type="datetime-local"
            value={values.expiresAt}
            onChange={(event) => onChange("expiresAt", event.target.value)}
            className={inputClassName}
          />
        </Field>
      </div>

      <div className="flex flex-wrap items-center gap-3 pt-2">
        <button
          type="submit"
          disabled={isSubmitting}
          className={primaryButtonClassName}
        >
          {isSubmitting ? "Creating short URL..." : "Create short URL"}
        </button>
        <p className="text-sm text-ink/55">
          Alias and expiry are optional. The backend will generate a short code if you leave alias blank.
        </p>
      </div>
    </form>
  );
}

function Field(props: {
  label: string;
  htmlFor: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="grid gap-2" htmlFor={props.htmlFor}>
      <span className="flex items-center justify-between gap-3 text-sm font-semibold text-ink">
        <span>{props.label}</span>
        {props.hint ? <span className="text-xs font-medium uppercase tracking-[0.2em] text-ink/40">{props.hint}</span> : null}
      </span>
      {props.children}
    </label>
  );
}

const inputClassName =
  "w-full rounded-2xl border border-ink/12 bg-mist/60 px-4 py-3 text-sm text-ink outline-none transition placeholder:text-ink/35 focus:border-pine/40 focus:bg-white";

const primaryButtonClassName =
  "inline-flex items-center justify-center rounded-full bg-ink px-5 py-3 text-sm font-semibold text-white transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40";
