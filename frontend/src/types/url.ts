export interface CreateShortUrlRequest {
  originalUrl: string;
  customAlias?: string;
  expiresAt?: string;
}

export interface ShortUrlRecord {
  id: string;
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  totalClicks: number;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}
