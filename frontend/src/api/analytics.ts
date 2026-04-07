import { requireResponseBody } from "@/api/apiUtils";
import { httpClient } from "@/api/httpClient";
import type { UrlAnalytics } from "@/types/analytics";

export function getUrlAnalytics(shortCode: string, from: string, to: string) {
  const searchParams = new URLSearchParams({ from, to });

  return requireResponseBody(
    httpClient.get<UrlAnalytics>(
      `/api/v1/urls/${shortCode}/analytics?${searchParams.toString()}`,
      { auth: true },
    ),
    "Analytics response body was empty.",
  );
}
