export interface ApiErrorPayload {
  error: string;
  message: string;
  status: number;
  timestamp: string;
  path: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly path?: string;

  constructor(payload: ApiErrorPayload) {
    super(payload.message);
    this.name = "ApiError";
    this.status = payload.status;
    this.code = payload.error;
    this.path = payload.path;
  }
}
