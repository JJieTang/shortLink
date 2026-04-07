import { MissingAccessTokenError, RequestTimeoutError } from "@/api/httpClient";
import { ApiError } from "@/types/api";

interface FeedbackErrorMessageOptions {
  fallback: string;
  timeoutMessage?: string;
  missingSessionMessage?: string;
}

export function toFeedbackErrorMessage(
  error: unknown,
  {
    fallback,
    timeoutMessage = "The request took too long. Please try again.",
    missingSessionMessage = "Your session is missing. Please sign in again.",
  }: FeedbackErrorMessageOptions,
) {
  if (error instanceof ApiError) {
    return error.message;
  }

  if (error instanceof MissingAccessTokenError) {
    return missingSessionMessage;
  }

  if (error instanceof RequestTimeoutError) {
    return timeoutMessage;
  }

  return fallback;
}
