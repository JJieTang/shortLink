import {
  createContext,
  useContext,
  type PropsWithChildren,
} from "react";
import { useAuthSession } from "@/hooks/useAuthSession";

type AuthSessionContextValue = ReturnType<typeof useAuthSession>;

const AuthSessionContext = createContext<AuthSessionContextValue | null>(null);

export function AuthSessionProvider({ children }: PropsWithChildren) {
  const value = useAuthSession();

  return (
    <AuthSessionContext.Provider value={value}>
      {children}
    </AuthSessionContext.Provider>
  );
}

export function useAuthSessionContext() {
  const value = useContext(AuthSessionContext);

  if (!value) {
    throw new Error("useAuthSessionContext must be used inside AuthSessionProvider");
  }

  return value;
}
