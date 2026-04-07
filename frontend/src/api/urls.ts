import { requireResponseBody } from "@/api/apiUtils";
import { httpClient } from "@/api/httpClient";
import type {
  CreateShortUrlRequest,
  PageResponse,
  ShortUrlRecord,
} from "@/types/url";

export function createShortUrl(payload: CreateShortUrlRequest) {
  return requireResponseBody(
    httpClient.post<ShortUrlRecord>("/api/v1/urls", payload, { auth: true }),
    "Create URL response body was empty.",
  );
}

export function listShortUrls(page: number, size: number) {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: "createdAt,desc",
  });

  return requireResponseBody(
    httpClient.get<PageResponse<ShortUrlRecord>>(`/api/v1/urls?${searchParams.toString()}`, {
      auth: true,
    }),
    "List URLs response body was empty.",
  );
}

export function deleteShortUrl(shortCode: string) {
  return httpClient.delete<void>(`/api/v1/urls/${shortCode}`, {
    auth: true,
  });
}
