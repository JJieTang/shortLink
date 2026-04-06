interface AuthFeedbackBannerProps {
  tone: "success" | "error" | "info";
  message: string;
}

export function AuthFeedbackBanner({
  tone,
  message,
}: AuthFeedbackBannerProps) {
  const toneClassName =
    tone === "success"
      ? "border-pine/20 bg-pine/10 text-pine"
      : tone === "info"
        ? "border-gold/20 bg-gold/10 text-ink"
        : "border-ember/20 bg-ember/10 text-ember";

  return (
    <div className={`mt-5 rounded-3xl border px-4 py-3 text-sm font-medium ${toneClassName}`}>
      {message}
    </div>
  );
}
