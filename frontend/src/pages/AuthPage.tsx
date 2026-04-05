import { useState, type FormEvent, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { loginUser, refreshSessionToken, registerUser } from "@/api/auth";
import { useAuthSessionContext } from "@/context/AuthSessionContext";
import { ApiError } from "@/types/api";

type AuthMode = "login" | "register";
type FeedbackTone = "success" | "error" | "info";

interface FeedbackState {
  tone: FeedbackTone;
  message: string;
}

export function AuthPage() {
  const navigate = useNavigate();
  const { session, setSession, clearSession, isAuthenticated } = useAuthSessionContext();

  const [mode, setMode] = useState<AuthMode>("login");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");

  const [registerName, setRegisterName] = useState("");
  const [registerEmail, setRegisterEmail] = useState("");
  const [registerPassword, setRegisterPassword] = useState("");

  async function handleLoginSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setFeedback(null);

    try {
      const response = await loginUser({
        email: loginEmail.trim(),
        password: loginPassword,
      });

      setSession({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        email: loginEmail.trim(),
      });
      setFeedback({
        tone: "success",
        message: "Signed in successfully. Redirecting you to the links workspace.",
      });
      setLoginPassword("");
      window.setTimeout(() => {
        navigate("/links");
      }, 500);
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toMessage(error, "Login failed. Please try again."),
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRegisterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setFeedback(null);

    try {
      const response = await registerUser({
        email: registerEmail.trim(),
        password: registerPassword,
        name: registerName.trim(),
      });

      setMode("login");
      setLoginEmail(response.email);
      setLoginPassword("");
      setRegisterPassword("");
      setFeedback({
        tone: "success",
        message: `Account created for ${response.email}. Sign in to start managing links.`,
      });
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toMessage(error, "Registration failed. Please review your inputs."),
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRefreshSession() {
    if (!session?.refreshToken) {
      setFeedback({
        tone: "info",
        message: "No refresh token is stored yet. Sign in first to rotate the session.",
      });
      return;
    }

    setIsSubmitting(true);
    setFeedback(null);

    try {
      const response = await refreshSessionToken({
        refreshToken: session.refreshToken,
      });

      setSession({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        email: session.email,
      });
      setFeedback({
        tone: "success",
        message: "Session rotated successfully.",
      });
    } catch (error) {
      clearSession();
      setFeedback({
        tone: "error",
        message: `${toMessage(error, "Session refresh failed.")} You have been signed out.`,
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-ember">Authentication</p>
        <h2 className="text-2xl font-semibold tracking-tight text-ink">Register, sign in, and rotate sessions</h2>
        <p className="max-w-3xl text-sm text-ink/70">
          This page is now wired to the backend auth endpoints. It can create accounts, exchange
          credentials for tokens, persist the active session, and rotate refresh tokens.
        </p>
      </header>

      <div className="grid gap-5 xl:grid-cols-[1.2fr_0.8fr]">
        <section className="rounded-[32px] border border-ink/10 bg-white px-6 py-6 shadow-sm">
          <div className="flex flex-wrap gap-3">
            <ModeButton active={mode === "login"} onClick={() => setMode("login")}>
              Login
            </ModeButton>
            <ModeButton active={mode === "register"} onClick={() => setMode("register")}>
              Register
            </ModeButton>
          </div>

          {feedback ? <FeedbackBanner feedback={feedback} /> : null}

          <div className="mt-6">
            {mode === "login" ? (
              <form className="grid gap-4" onSubmit={handleLoginSubmit}>
                <Field label="Email" htmlFor="login-email">
                  <input
                    id="login-email"
                    type="email"
                    autoComplete="email"
                    required
                    value={loginEmail}
                    onChange={(event) => setLoginEmail(event.target.value)}
                    className={inputClassName}
                    placeholder="you@example.com"
                  />
                </Field>

                <Field label="Password" htmlFor="login-password">
                  <input
                    id="login-password"
                    type="password"
                    autoComplete="current-password"
                    required
                    minLength={8}
                    value={loginPassword}
                    onChange={(event) => setLoginPassword(event.target.value)}
                    className={inputClassName}
                    placeholder="At least 8 characters"
                  />
                </Field>

                <div className="flex flex-wrap gap-3 pt-2">
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className={primaryButtonClassName}
                  >
                    {isSubmitting ? "Signing in..." : "Sign in"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setMode("register")}
                    className={secondaryButtonClassName}
                  >
                    Need an account?
                  </button>
                </div>
              </form>
            ) : (
              <form className="grid gap-4" onSubmit={handleRegisterSubmit}>
                <Field label="Name" htmlFor="register-name">
                  <input
                    id="register-name"
                    type="text"
                    autoComplete="name"
                    required
                    value={registerName}
                    onChange={(event) => setRegisterName(event.target.value)}
                    className={inputClassName}
                    placeholder="ShortLink Operator"
                  />
                </Field>

                <Field label="Email" htmlFor="register-email">
                  <input
                    id="register-email"
                    type="email"
                    autoComplete="email"
                    required
                    value={registerEmail}
                    onChange={(event) => setRegisterEmail(event.target.value)}
                    className={inputClassName}
                    placeholder="you@example.com"
                  />
                </Field>

                <Field label="Password" htmlFor="register-password">
                  <input
                    id="register-password"
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={8}
                    value={registerPassword}
                    onChange={(event) => setRegisterPassword(event.target.value)}
                    className={inputClassName}
                    placeholder="Must include one uppercase letter and one digit"
                  />
                </Field>

                <div className="flex flex-wrap gap-3 pt-2">
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className={primaryButtonClassName}
                  >
                    {isSubmitting ? "Creating account..." : "Create account"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setMode("login")}
                    className={secondaryButtonClassName}
                  >
                    Already registered?
                  </button>
                </div>
              </form>
            )}
          </div>
        </section>

        <aside className="grid gap-4">
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
                onClick={handleRefreshSession}
                disabled={isSubmitting}
                className={primaryButtonClassName}
              >
                Refresh session
              </button>
              <button
                type="button"
                onClick={clearSession}
                className={secondaryButtonClassName}
              >
                Clear session
              </button>
            </div>
          </article>

          <article className="rounded-[32px] border border-ink/10 bg-white px-5 py-5 shadow-sm">
            <p className="text-xs uppercase tracking-[0.24em] text-ember">What this commit adds</p>
            <ul className="mt-4 grid gap-3 text-sm leading-6 text-ink/68">
              <li>Real login and register forms wired to the Spring Boot auth endpoints.</li>
              <li>Session persistence in localStorage plus app-wide auth state through context.</li>
              <li>Refresh-token rotation support so the shell can keep a session alive.</li>
            </ul>
          </article>
        </aside>
      </div>
    </section>
  );
}

function FeedbackBanner({ feedback }: { feedback: FeedbackState }) {
  const toneClassName =
    feedback.tone === "success"
      ? "border-pine/20 bg-pine/10 text-pine"
      : feedback.tone === "info"
        ? "border-gold/20 bg-gold/10 text-ink"
        : "border-ember/20 bg-ember/10 text-ember";

  return (
    <div className={`mt-5 rounded-3xl border px-4 py-3 text-sm font-medium ${toneClassName}`}>
      {feedback.message}
    </div>
  );
}

function ModeButton(props: {
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

function Field(props: {
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

function toMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

const inputClassName =
  "w-full rounded-2xl border border-ink/12 bg-mist/60 px-4 py-3 text-sm text-ink outline-none transition placeholder:text-ink/35 focus:border-pine/40 focus:bg-white";

const primaryButtonClassName =
  "inline-flex items-center justify-center rounded-full bg-ink px-5 py-3 text-sm font-semibold text-white transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40";

const secondaryButtonClassName =
  "inline-flex items-center justify-center rounded-full border border-ink/10 px-5 py-3 text-sm font-semibold text-ink transition hover:border-ink/20 hover:bg-mist";
