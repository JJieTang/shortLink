import { useEffect, useRef, useState } from "react";
import type { AuthSession } from "@/types/auth";
import { readSession, writeSession } from "@/api/sessionStore";

export function useAuthSession() {
  const [session, setSession] = useState<AuthSession | null>(() => readSession());
  const hasPersistedInitialSession = useRef(false);

  useEffect(() => {
    if (!hasPersistedInitialSession.current) {
      hasPersistedInitialSession.current = true;
      return;
    }

    writeSession(session);
  }, [session]);

  return {
    session,
    isAuthenticated: hasActiveAccessToken(session),
    setSession,
    clearSession: () => setSession(null),
  };
}

function hasActiveAccessToken(session: AuthSession | null) {
  if (!session?.accessToken) {
    return false;
  }

  const expiresAt = readJwtExpiration(session.accessToken);
  if (!expiresAt) {
    return true;
  }

  return expiresAt > Date.now();
}

function readJwtExpiration(token: string) {
  const parts = token.split(".");
  if (parts.length < 2) {
    return null;
  }

  try {
    const payload = JSON.parse(decodeBase64Url(parts[1])) as { exp?: number };
    if (typeof payload.exp !== "number") {
      return null;
    }

    return payload.exp * 1000;
  } catch {
    return null;
  }
}

function decodeBase64Url(value: string) {
  const normalizedValue = value.replace(/-/g, "+").replace(/_/g, "/");
  const paddingLength = (4 - (normalizedValue.length % 4)) % 4;
  const paddedValue = normalizedValue.padEnd(normalizedValue.length + paddingLength, "=");

  return window.atob(paddedValue);
}
