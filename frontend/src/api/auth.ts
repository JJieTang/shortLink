import { httpClient } from "@/api/httpClient";
import type {
  AuthResponse,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  RegisterResponse,
} from "@/types/auth";

export function registerUser(payload: RegisterRequest) {
  return requireResponseBody(
    httpClient.post<RegisterResponse>("/api/v1/auth/register", payload),
    "Registration response body was empty.",
  );
}

export function loginUser(payload: LoginRequest) {
  return requireResponseBody(
    httpClient.post<AuthResponse>("/api/v1/auth/login", payload),
    "Login response body was empty.",
  );
}

export function refreshSessionToken(payload: RefreshTokenRequest) {
  return requireResponseBody(
    httpClient.post<AuthResponse>("/api/v1/auth/refresh", payload),
    "Refresh response body was empty.",
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
