import { Suspense, lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "@/components/AppShell";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AuthPage } from "@/pages/AuthPage";
import { LinksPage } from "@/pages/LinksPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

const AnalyticsPage = lazy(async () => {
  const module = await import("@/pages/AnalyticsPage");
  return { default: module.AnalyticsPage };
});

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/links" replace />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/links" element={<LinksPage />} />
          <Route
            path="/analytics"
            element={(
              <Suspense fallback={<AnalyticsRouteFallback />}>
                <AnalyticsPage />
              </Suspense>
            )}
          />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

function AnalyticsRouteFallback() {
  return (
    <section className="space-y-4">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-[0.3em] text-gold">Analytics</p>
        <h2 className="text-2xl font-semibold tracking-tight text-ink">Loading analytics workspace</h2>
      </header>

      <div className="rounded-[28px] border border-dashed border-ink/12 bg-white/75 px-6 py-10 text-sm text-ink/62 shadow-sm">
        Loading the analytics route and chart bundle...
      </div>
    </section>
  );
}
