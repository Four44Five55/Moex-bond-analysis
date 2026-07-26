import { API_BASE, apiGet, apiGetWithClient, apiPost, apiDelete } from "./client";
import type {
  Candle,
  DividendInfo,
  Instrument,
  InstrumentFilters,
  InstrumentPayment,
  InstrumentPerformance,
  PagedResponse,
} from "./types";

function toQuery(f: InstrumentFilters): string {
  const p = new URLSearchParams();
  p.set("type", f.type);
  if (f.query) p.set("query", f.query);
  if (f.minYield != null) p.set("minYield", String(f.minYield));
  if (f.maxDuration != null) p.set("maxDuration", String(f.maxDuration));
  if (f.minReliability != null) p.set("minReliability", String(f.minReliability));
  if (f.onlyInvestmentGrade != null) p.set("onlyInvestmentGrade", String(f.onlyInvestmentGrade));
  if (f.excludeSuspicious != null) p.set("excludeSuspicious", String(f.excludeSuspicious));
  if (f.sector) p.set("sector", f.sector);
  if (f.page != null) p.set("page", String(f.page));
  if (f.size != null) p.set("size", String(f.size));
  if (f.sort) p.set("sort", f.sort);
  return p.toString();
}

export const instrumentsApi = {
  list: (f: InstrumentFilters) =>
    apiGet<PagedResponse<Instrument>>(`/instruments?${toQuery(f)}`),

  search: (query: string) =>
    apiGet<PagedResponse<Instrument>>(`/instruments/search?query=${encodeURIComponent(query)}&size=15`),

  detail: (secId: string) => apiGet<Instrument>(`/instruments/${encodeURIComponent(secId)}`),

  candles: (
    secId: string,
    opts: { interval?: number; from?: string; to?: string } = {}
  ) => {
    const p = new URLSearchParams();
    p.set("interval", String(opts.interval ?? 24));
    if (opts.from) p.set("from", opts.from);
    if (opts.to) p.set("to", opts.to);
    return apiGet<Candle[]>(`/instruments/${encodeURIComponent(secId)}/candles?${p.toString()}`);
  },

  payments: (secId: string) =>
    apiGet<InstrumentPayment[]>(`/instruments/${encodeURIComponent(secId)}/payments`),

  performance: (secId: string) =>
    apiGet<InstrumentPerformance>(`/instruments/${encodeURIComponent(secId)}/performance`),

  dividendYield: (secId: string) =>
    apiGet<DividendInfo>(`/instruments/${encodeURIComponent(secId)}/dividend-yield`),

  compare: (secIds: string[]) =>
    apiGet<Instrument[]>(`/instruments/compare?secIds=${secIds.map(encodeURIComponent).join(",")}`),

  refresh: () => apiPost<{ synced: number }>("/instruments/refresh"),

  exportUrl: (f: InstrumentFilters) => `${API_BASE}/export?${toQuery(f)}`,
};

export const watchlistApi = {
  list: () => apiGetWithClient<Instrument[]>("/watchlist"),
  add: (secId: string) => apiPost<void>("/watchlist", { secId }, true),
  remove: (secId: string) => apiDelete<void>(`/watchlist/${encodeURIComponent(secId)}`, true),
};
