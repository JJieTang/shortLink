import type { FormEvent } from "react";
import {
  AuthField,
  primaryButtonClassName,
  secondaryButtonClassName,
  inputClassName,
} from "@/components/auth/authUi";

interface LoginFormProps {
  email: string;
  password: string;
  isSubmitting: boolean;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onNeedAccount: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

export function LoginForm({
  email,
  password,
  isSubmitting,
  onEmailChange,
  onPasswordChange,
  onNeedAccount,
  onSubmit,
}: LoginFormProps) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <AuthField label="Email" htmlFor="login-email">
        <input
          id="login-email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => onEmailChange(event.target.value)}
          className={inputClassName}
          placeholder="you@example.com"
        />
      </AuthField>

      <AuthField label="Password" htmlFor="login-password">
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          required
          minLength={8}
          value={password}
          onChange={(event) => onPasswordChange(event.target.value)}
          className={inputClassName}
          placeholder="At least 8 characters"
        />
      </AuthField>

      <div className="flex flex-wrap gap-3 pt-2">
        <button type="submit" disabled={isSubmitting} className={primaryButtonClassName}>
          {isSubmitting ? "Signing in..." : "Sign in"}
        </button>
        <button
          type="button"
          onClick={onNeedAccount}
          className={secondaryButtonClassName}
        >
          Need an account?
        </button>
      </div>
    </form>
  );
}
