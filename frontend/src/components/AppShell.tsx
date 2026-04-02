import { NavLink, Outlet } from "react-router-dom";

const navigation = [
  { to: "/links", label: "Links", caption: "Create and manage short URLs" },
  { to: "/analytics", label: "Analytics", caption: "Review traffic and trends" },
  { to: "/auth", label: "Auth", caption: "Sign in and refresh sessions" },
];

export function AppShell() {
  return (
    <div className="shell-grid min-h-screen px-4 py-6 text-ink sm:px-6 lg:px-8">
      <div className="mx-auto flex min-h-[calc(100vh-3rem)] w-full max-w-7xl flex-col overflow-hidden rounded-[32px] border border-white/70 bg-white/75 shadow-shell backdrop-blur">
        <header className="border-b border-ink/10 px-6 py-5 sm:px-8">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="space-y-3">
              <span className="inline-flex w-fit items-center rounded-full bg-pine/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.28em] text-pine">
                Phase 4 Frontend
              </span>
              <div>
                <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">
                  ShortLink control room
                </h1>
                <p className="mt-2 max-w-2xl text-sm text-ink/70 sm:text-base">
                  A web shell for link operations, authentication flows, and analytics.
                  This first commit establishes the shared navigation, layout, and client
                  infrastructure for the rest of Phase 4.
                </p>
              </div>
            </div>
            <div className="grid gap-3 rounded-3xl border border-ink/10 bg-mist/80 p-4 sm:grid-cols-3">
              <MetricCard label="API Base" value="env-driven" />
              <MetricCard label="Routes" value="3 core views" />
              <MetricCard label="Theme" value="tailwind shell" />
            </div>
          </div>
        </header>

        <div className="flex flex-1 flex-col lg:flex-row">
          <aside className="border-b border-ink/10 bg-[#f7f0e4]/80 px-4 py-4 lg:w-80 lg:border-b-0 lg:border-r lg:px-5">
            <nav className="grid gap-3">
              {navigation.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    [
                      "rounded-3xl border px-4 py-4 transition",
                      isActive
                        ? "border-pine bg-pine text-white"
                        : "border-transparent bg-white/80 text-ink hover:border-ink/10 hover:bg-white",
                    ].join(" ")
                  }
                >
                  {({ isActive }) => (
                    <div className="space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="text-lg font-semibold">{item.label}</span>
                        <span
                          className={[
                            "rounded-full px-2 py-1 text-[11px] uppercase tracking-[0.24em]",
                            isActive ? "bg-white/20 text-white" : "bg-ink/5 text-ink/55",
                          ].join(" ")}
                        >
                          View
                        </span>
                      </div>
                      <p
                        className={[
                          "text-sm",
                          isActive ? "text-white/76" : "text-ink/65",
                        ].join(" ")}
                      >
                        {item.caption}
                      </p>
                    </div>
                  )}
                </NavLink>
              ))}
            </nav>
          </aside>

          <main className="flex-1 overflow-auto px-4 py-5 sm:px-6 lg:px-8 lg:py-8">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-white px-4 py-3">
      <p className="text-[11px] uppercase tracking-[0.24em] text-ink/45">{label}</p>
      <p className="mt-2 text-sm font-semibold text-ink">{value}</p>
    </div>
  );
}
