import { Navigate, Outlet } from "react-router-dom";
import { useAuthSessionContext } from "@/context/AuthSessionContext";

export function ProtectedRoute() {
  const { isAuthenticated } = useAuthSessionContext();

  if (!isAuthenticated) {
    return <Navigate to="/auth" replace />;
  }

  return <Outlet />;
}
