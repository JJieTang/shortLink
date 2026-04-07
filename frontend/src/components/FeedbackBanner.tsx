import type { FeedbackTone } from "@/types/feedback";

interface FeedbackBannerProps {
  tone: FeedbackTone;
  message: string;
  className?: string;
}

export function FeedbackBanner({
  tone,
  message,
  className = "",
}: FeedbackBannerProps) {
  const toneClassName =
    tone === "success"
      ? "border-pine/20 bg-pine/10 text-pine"
      : tone === "info"
        ? "border-gold/20 bg-gold/10 text-ink"
        : "border-ember/20 bg-ember/10 text-ember";

  return (
    <div
      className={`rounded-3xl border px-4 py-3 text-sm font-medium ${toneClassName} ${className}`.trim()}
    >
      {message}
    </div>
  );
}
