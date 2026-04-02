import { useEffect, useState } from "react";
import type { AuthSession } from "@/types/api";
import { readSession, writeSession } from "@/api/sessionStore";

export function useAuthSession() {
  const [session, setSession] = useState<AuthSession | null>(() => readSession());

  useEffect(() => {
    writeSession(session);
  }, [session]);

  return {
    session,
    isAuthenticated: Boolean(session?.accessToken),
    setSession,
    clearSession: () => setSession(null),
  };
}
