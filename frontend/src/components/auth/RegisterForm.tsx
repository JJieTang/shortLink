import type { FormEvent } from "react";
import {
  AuthField,
  inputClassName,
  primaryButtonClassName,
  secondaryButtonClassName,
} from "@/components/auth/authUi";

interface RegisterFormProps {
  name: string;
  email: string;
  password: string;
  isSubmitting: boolean;
  onNameChange: (value: string) => void;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onAlreadyRegistered: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

export function RegisterForm({
  name,
  email,
  password,
  isSubmitting,
  onNameChange,
  onEmailChange,
  onPasswordChange,
  onAlreadyRegistered,
  onSubmit,
}: RegisterFormProps) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <AuthField label="Name" htmlFor="register-name">
        <input
          id="register-name"
          type="text"
          autoComplete="name"
          required
          value={name}
          onChange={(event) => onNameChange(event.target.value)}
          className={inputClassName}
          placeholder="ShortLink Operator"
        />
      </AuthField>

      <AuthField label="Email" htmlFor="register-email">
        <input
          id="register-email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => onEmailChange(event.target.value)}
          className={inputClassName}
          placeholder="you@example.com"
        />
      </AuthField>

      <AuthField label="Password" htmlFor="register-password">
        <input
          id="register-password"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          value={password}
          onChange={(event) => onPasswordChange(event.target.value)}
          className={inputClassName}
          placeholder="Must include one uppercase letter and one digit"
        />
      </AuthField>

      <div className="flex flex-wrap gap-3 pt-2">
        <button type="submit" disabled={isSubmitting} className={primaryButtonClassName}>
          {isSubmitting ? "Creating account..." : "Create account"}
        </button>
        <button
          type="button"
          onClick={onAlreadyRegistered}
          className={secondaryButtonClassName}
        >
          Already registered?
        </button>
      </div>
    </form>
  );
}
