import { BrowserRouter } from "react-router-dom";
import { render } from "@testing-library/react";
import type { ReactElement, PropsWithChildren } from "react";
import { AuthSessionProvider } from "@/context/AuthSessionContext";

function AppProviders({ children }: PropsWithChildren) {
  return (
    <BrowserRouter>
      <AuthSessionProvider>{children}</AuthSessionProvider>
    </BrowserRouter>
  );
}

export function renderWithAppProviders(ui: ReactElement) {
  return render(ui, { wrapper: AppProviders });
}
