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

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
