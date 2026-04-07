import { useEffect, useReducer, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { loginUser, refreshSessionToken, registerUser } from "@/api/auth";
import { AuthFeedbackBanner } from "@/components/auth/AuthFeedbackBanner";
import { LoginForm } from "@/components/auth/LoginForm";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { SessionPanel } from "@/components/auth/SessionPanel";
import { ModeButton } from "@/components/auth/authUi";
import { useAuthSessionContext } from "@/context/AuthSessionContext";
import { ApiError } from "@/types/api";
import type { AuthSession } from "@/types/auth";
import type { FeedbackState } from "@/types/feedback";
import { toFeedbackErrorMessage } from "@/utils/errorMessage";

type AuthMode = "login" | "register";

interface AuthFormsState {
  login: {
    email: string;
    password: string;
  };
  register: {
    name: string;
    email: string;
    password: string;
  };
}

type AuthFormsAction =
  | { type: "update-login"; field: "email" | "password"; value: string }
  | { type: "update-register"; field: "name" | "email" | "password"; value: string }
  | { type: "reset-login"; email?: string }
  | { type: "reset-register" }
  | { type: "clear-login-password" };

const INITIAL_AUTH_FORMS: AuthFormsState = {
  login: {
    email: "",
    password: "",
  },
  register: {
    name: "",
    email: "",
    password: "",
  },
};

export function AuthPage() {
  const navigate = useNavigate();
  const { session, setSession, clearSession, isAuthenticated } = useAuthSessionContext();

  const [mode, setMode] = useState<AuthMode>("login");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [pendingRedirect, setPendingRedirect] = useState(false);
  const [forms, dispatch] = useReducer(authFormsReducer, INITIAL_AUTH_FORMS);

  useEffect(() => {
    if (!pendingRedirect || !isAuthenticated) {
      return;
    }

    setPendingRedirect(false);
    navigate("/links", { replace: true });
  }, [isAuthenticated, navigate, pendingRedirect]);

  function handleModeChange(nextMode: AuthMode) {
    setMode(nextMode);
    setFeedback(null);

    if (nextMode === "login") {
      dispatch({ type: "reset-login" });
      return;
    }

    dispatch({ type: "reset-register" });
  }

  async function handleLoginSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setFeedback(null);

    try {
      const response = await loginUser({
        email: forms.login.email.trim(),
        password: forms.login.password,
      });

      const nextSession: AuthSession = {
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        email: forms.login.email.trim(),
      };

      setSession(nextSession);
      setFeedback({
        tone: "success",
        message: "Signed in successfully. Opening the links workspace now.",
      });
      dispatch({ type: "clear-login-password" });
      setPendingRedirect(true);
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toFeedbackErrorMessage(error, {
          fallback: "Login failed. Please try again.",
          timeoutMessage: "The login request took too long. Please try again.",
        }),
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
        email: forms.register.email.trim(),
        password: forms.register.password,
        name: forms.register.name.trim(),
      });

      setMode("login");
      dispatch({ type: "reset-login", email: response.email });
      dispatch({ type: "reset-register" });
      setFeedback({
        tone: "success",
        message: `Account created for ${response.email}. Sign in to start managing links.`,
      });
    } catch (error) {
      setFeedback({
        tone: "error",
        message: toFeedbackErrorMessage(error, {
          fallback: "Registration failed. Please review your inputs.",
          timeoutMessage: "The registration request took too long. Please try again.",
        }),
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

      const nextSession: AuthSession = {
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        email: session.email,
      };

      setSession(nextSession);
      setFeedback({
        tone: "success",
        message: "Session rotated successfully.",
      });
    } catch (error) {
      const shouldSignOut = shouldClearSessionOnRefreshFailure(error);

      if (shouldSignOut) {
        clearSession();
      }

      setFeedback({
        tone: "error",
        message: shouldSignOut
          ? `${toFeedbackErrorMessage(error, {
              fallback: "Session refresh failed.",
              timeoutMessage: "The refresh request took too long. Please try again.",
            })} You have been signed out.`
          : toFeedbackErrorMessage(error, {
              fallback: "Session refresh failed. Please try again.",
              timeoutMessage: "The refresh request took too long. Please try again.",
            }),
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
          This page is wired to the backend auth endpoints and now keeps the auth forms, feedback,
          and session actions in smaller components.
        </p>
      </header>

      <div className="grid gap-5 xl:grid-cols-[1.2fr_0.8fr]">
        <section className="rounded-[32px] border border-ink/10 bg-white px-6 py-6 shadow-sm">
          <div className="flex flex-wrap gap-3">
            <ModeButton active={mode === "login"} onClick={() => handleModeChange("login")}>
              Login
            </ModeButton>
            <ModeButton active={mode === "register"} onClick={() => handleModeChange("register")}>
              Register
            </ModeButton>
          </div>

          {feedback ? <AuthFeedbackBanner tone={feedback.tone} message={feedback.message} /> : null}

          <div className="mt-6">
            {mode === "login" ? (
              <LoginForm
                email={forms.login.email}
                password={forms.login.password}
                isSubmitting={isSubmitting}
                onEmailChange={(value) =>
                  dispatch({ type: "update-login", field: "email", value })
                }
                onPasswordChange={(value) =>
                  dispatch({ type: "update-login", field: "password", value })
                }
                onNeedAccount={() => handleModeChange("register")}
                onSubmit={handleLoginSubmit}
              />
            ) : (
              <RegisterForm
                name={forms.register.name}
                email={forms.register.email}
                password={forms.register.password}
                isSubmitting={isSubmitting}
                onNameChange={(value) =>
                  dispatch({ type: "update-register", field: "name", value })
                }
                onEmailChange={(value) =>
                  dispatch({ type: "update-register", field: "email", value })
                }
                onPasswordChange={(value) =>
                  dispatch({ type: "update-register", field: "password", value })
                }
                onAlreadyRegistered={() => handleModeChange("login")}
                onSubmit={handleRegisterSubmit}
              />
            )}
          </div>
        </section>

        <aside className="grid gap-4">
          <SessionPanel
            session={session}
            isAuthenticated={isAuthenticated}
            isSubmitting={isSubmitting}
            onRefresh={handleRefreshSession}
            onClearSession={clearSession}
          />

          <article className="rounded-[32px] border border-ink/10 bg-white px-5 py-5 shadow-sm">
            <p className="text-xs uppercase tracking-[0.24em] text-ember">What this commit adds</p>
            <ul className="mt-4 grid gap-3 text-sm leading-6 text-ink/68">
              <li>Auth forms are split into smaller components with a shared reducer for field state.</li>
              <li>Login redirect is now state-driven instead of using a hard-coded timeout.</li>
              <li>Refresh failures only sign the user out when the backend says the session is invalid.</li>
            </ul>
          </article>
        </aside>
      </div>
    </section>
  );
}

function authFormsReducer(state: AuthFormsState, action: AuthFormsAction): AuthFormsState {
  switch (action.type) {
    case "update-login":
      return {
        ...state,
        login: {
          ...state.login,
          [action.field]: action.value,
        },
      };
    case "update-register":
      return {
        ...state,
        register: {
          ...state.register,
          [action.field]: action.value,
        },
      };
    case "reset-login":
      return {
        ...state,
        login: {
          email: action.email ?? "",
          password: "",
        },
      };
    case "reset-register":
      return {
        ...state,
        register: {
          name: "",
          email: "",
          password: "",
        },
      };
    case "clear-login-password":
      return {
        ...state,
        login: {
          ...state.login,
          password: "",
        },
      };
    default:
      return state;
  }
}

function shouldClearSessionOnRefreshFailure(error: unknown) {
  return error instanceof ApiError && error.status >= 400 && error.status < 500;
}
