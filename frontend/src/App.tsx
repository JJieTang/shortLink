import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "@/components/AppShell";
import { AnalyticsPage } from "@/pages/AnalyticsPage";
import { AuthPage } from "@/pages/AuthPage";
import { LinksPage } from "@/pages/LinksPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/links" replace />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/links" element={<LinksPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
