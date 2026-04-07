export interface DailyClicks {
  date: string;
  clicks: number;
}

export interface UrlAnalytics {
  shortCode: string;
  totalClicks: number;
  periodClicks: number;
  uniqueClicks: number;
  clicksByDate: DailyClicks[];
}
