import {
  createContext,
  useEffect,
  useContext,
  type PropsWithChildren,
} from "react";
import { useNavigate } from "react-router-dom";
import { setUnauthorizedHandler } from "@/api/httpClient";
import { useAuthSession } from "@/hooks/useAuthSession";

type AuthSessionContextValue = ReturnType<typeof useAuthSession>;

const AuthSessionContext = createContext<AuthSessionContextValue | null>(null);

export function AuthSessionProvider({ children }: PropsWithChildren) {
  const value = useAuthSession();
  const navigate = useNavigate();

  useEffect(() => {
    setUnauthorizedHandler(() => {
      value.clearSession();
      navigate("/auth", { replace: true });
    });

    return () => {
      setUnauthorizedHandler(null);
    };
  }, [navigate, value]);

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
