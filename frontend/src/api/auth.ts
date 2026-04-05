import { httpClient } from "@/api/httpClient";
import type {
  AuthResponse,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  RegisterResponse,
} from "@/types/auth";

export function registerUser(payload: RegisterRequest) {
  return httpClient.post<RegisterResponse>("/api/v1/auth/register", payload);
}

export function loginUser(payload: LoginRequest) {
  return httpClient.post<AuthResponse>("/api/v1/auth/login", payload);
}

export function refreshSessionToken(payload: RefreshTokenRequest) {
  return httpClient.post<AuthResponse>("/api/v1/auth/refresh", payload);
}
