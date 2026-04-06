import type { ReactNode } from "react";

export function ModeButton(props: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      className={[
        "rounded-full px-4 py-2 text-sm font-semibold transition",
        props.active
          ? "bg-ink text-white"
          : "border border-ink/10 bg-mist text-ink hover:border-ink/20 hover:bg-white",
      ].join(" ")}
    >
      {props.children}
    </button>
  );
}

export function AuthField(props: {
  label: string;
  htmlFor: string;
  children: ReactNode;
}) {
  return (
    <label className="grid gap-2" htmlFor={props.htmlFor}>
      <span className="text-sm font-semibold text-ink">{props.label}</span>
      {props.children}
    </label>
  );
}

export const inputClassName =
  "w-full rounded-2xl border border-ink/12 bg-mist/60 px-4 py-3 text-sm text-ink outline-none transition placeholder:text-ink/35 focus:border-pine/40 focus:bg-white";

export const primaryButtonClassName =
  "inline-flex items-center justify-center rounded-full bg-ink px-5 py-3 text-sm font-semibold text-white transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40";

export const secondaryButtonClassName =
  "inline-flex items-center justify-center rounded-full border border-ink/10 px-5 py-3 text-sm font-semibold text-ink transition hover:border-ink/20 hover:bg-mist";
