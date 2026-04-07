import { ApiError, type ApiErrorPayload } from "@/types/api";
import { readAccessToken } from "./sessionStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const DEFAULT_TIMEOUT_MS = Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 10000);

type JsonPrimitive = string | number | boolean | null;
type JsonBody = JsonPrimitive | JsonPrimitive[] | object;
type UnauthorizedHandler = (error: ApiError) => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

export class MissingAccessTokenError extends Error {
  constructor() {
    super("Authenticated request requires a stored access token.");
    this.name = "MissingAccessTokenError";
  }
}

export class RequestTimeoutError extends Error {
  readonly path: string;
  readonly timeoutMs: number;

  constructor(path: string, timeoutMs: number) {
    super(`Request to ${path} timed out after ${timeoutMs}ms.`);
    this.name = "RequestTimeoutError";
    this.path = path;
    this.timeoutMs = timeoutMs;
  }
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  auth?: boolean;
  timeoutMs?: number;
}

interface InternalRequestOptions extends RequestOptions {
  body?: BodyInit | null;
}

async function request<T>(path: string, options: InternalRequestOptions = {}): Promise<T | undefined> {
  const headers = new Headers(options.headers);
  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const detachSignal = attachAbortListener(options.signal, controller);
  let timedOut = false;

  if (options.auth) {
    const accessToken = readAccessToken();
    if (!accessToken) {
      throw new MissingAccessTokenError();
    }

    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const timeoutId = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  try {
    const response = await fetch(resolveRequestUrl(path), {
      ...options,
      headers,
      signal: controller.signal,
    });

    if (!response.ok) {
      const payload = (await safeJson(response)) as ApiErrorPayload | null;
      const apiError = new ApiError(
        payload ?? {
          error: "HTTP_ERROR",
          message: response.statusText || "Request failed",
          status: response.status,
          timestamp: new Date().toISOString(),
          path,
        },
      );

      if (options.auth && response.status === 401) {
        unauthorizedHandler?.(apiError);
      }

      throw apiError;
    }

    if (response.status === 204) {
      return undefined;
    }

    return (await safeJson(response)) as T;
  } catch (error) {
    if (timedOut) {
      throw new RequestTimeoutError(path, timeoutMs);
    }

    throw error;
  } finally {
    window.clearTimeout(timeoutId);
    detachSignal();
  }
}

function resolveRequestUrl(path: string) {
  if (!API_BASE_URL) {
    return path;
  }

  return new URL(path, API_BASE_URL);
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

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

function withJsonContentType(headers?: HeadersInit) {
  const nextHeaders = new Headers(headers);

  if (!nextHeaders.has("Content-Type")) {
    nextHeaders.set("Content-Type", "application/json");
  }

  return nextHeaders;
}

function attachAbortListener(
  signal: AbortSignal | null | undefined,
  controller: AbortController,
) {
  if (!signal) {
    return () => {};
  }

  const abortController = () => {
    controller.abort();
  };

  if (signal.aborted) {
    abortController();
    return () => {};
  }

  signal.addEventListener("abort", abortController);

  return () => {
    signal.removeEventListener("abort", abortController);
  };
}
