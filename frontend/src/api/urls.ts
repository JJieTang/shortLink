import { httpClient } from "@/api/httpClient";
import type { CreateShortUrlRequest, ShortUrlRecord } from "@/types/url";

export function createShortUrl(payload: CreateShortUrlRequest) {
  return requireResponseBody(
    httpClient.post<ShortUrlRecord>("/api/v1/urls", payload, { auth: true }),
    "Create URL response body was empty.",
  );
}

async function requireResponseBody<T>(
  responsePromise: Promise<T | undefined>,
  message: string,
) {
  const response = await responsePromise;

  if (response === undefined) {
    throw new Error(message);
  }

  return response;
}
