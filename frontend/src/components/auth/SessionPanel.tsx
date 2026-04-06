import type { AuthSession } from "@/types/auth";
import {
  primaryButtonClassName,
  secondaryButtonClassName,
} from "@/components/auth/authUi";

interface SessionPanelProps {
  session: AuthSession | null;
  isAuthenticated: boolean;
  isSubmitting: boolean;
  onRefresh: () => void;
  onClearSession: () => void;
}

export function SessionPanel({
  session,
  isAuthenticated,
  isSubmitting,
  onRefresh,
  onClearSession,
}: SessionPanelProps) {
  return (
    <article className="rounded-[32px] border border-ink/10 bg-[linear-gradient(135deg,#fbf7f0_0%,#e8dcc6_100%)] px-5 py-5 shadow-sm">
      <p className="text-xs uppercase tracking-[0.24em] text-ink/45">Session state</p>
      <h3 className="mt-3 text-xl font-semibold text-ink">
        {isAuthenticated ? "Authenticated" : "Waiting for sign-in"}
      </h3>
      <p className="mt-2 text-sm leading-6 text-ink/68">
        {session?.email
          ? `Tokens for ${session.email} are stored locally and ready to be used by protected requests.`
          : "No active session is stored yet. Sign in to unlock the protected API routes."}
      </p>

      <div className="mt-5 flex flex-wrap gap-3">
        <button
          type="button"
          onClick={onRefresh}
          disabled={isSubmitting}
          className={primaryButtonClassName}
        >
          Refresh session
        </button>
        <button
          type="button"
          onClick={onClearSession}
          className={secondaryButtonClassName}
        >
          Clear session
        </button>
      </div>
    </article>
  );
}
