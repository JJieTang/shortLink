import { ApiError, type ApiErrorPayload } from "@/types/api";
import { readAccessToken } from "./sessionStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

type JsonPrimitive = string | number | boolean | null;
type JsonBody = JsonPrimitive | JsonPrimitive[] | object;

export class MissingAccessTokenError extends Error {
  constructor() {
    super("Authenticated request requires a stored access token.");
    this.name = "MissingAccessTokenError";
  }
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  auth?: boolean;
}

interface InternalRequestOptions extends RequestOptions {
  body?: BodyInit | null;
}

async function request<T>(path: string, options: InternalRequestOptions = {}): Promise<T | undefined> {
  const headers = new Headers(options.headers);

  if (options.auth) {
    const accessToken = readAccessToken();
    if (!accessToken) {
      throw new MissingAccessTokenError();
    }

    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(new URL(path, API_BASE_URL), {
    ...options,
    headers,
  });

  if (!response.ok) {
    const payload = (await safeJson(response)) as ApiErrorPayload | null;
    throw new ApiError(
      payload ?? {
        error: "HTTP_ERROR",
        message: response.statusText || "Request failed",
        status: response.status,
        timestamp: new Date().toISOString(),
        path,
      },
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await safeJson(response)) as T;
}

async function safeJson(response: Response) {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export const httpClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: JsonBody, options?: RequestOptions) =>
    request<T>(path, {
      ...options,
      method: "POST",
      headers: withJsonContentType(options?.headers),
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "DELETE" }),
};

function withJsonContentType(headers?: HeadersInit) {
  const nextHeaders = new Headers(headers);

  if (!nextHeaders.has("Content-Type")) {
    nextHeaders.set("Content-Type", "application/json");
  }

  return nextHeaders;
}
