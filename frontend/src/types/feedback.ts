export type FeedbackTone = "success" | "error" | "info";

export interface FeedbackState {
  tone: FeedbackTone;
  message: string;
}
