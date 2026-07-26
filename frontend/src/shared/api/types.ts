export type InstrumentType = "BOND" | "SHARE";

export interface Instrument {
  secId: string;
  type: InstrumentType;
  shortName: string | null;
  fullName: string | null;
  isin: string | null;
  currency: string | null;
  board: string | null;
  market: string | null;
  lastPrice: number | null;
  prevPrice: number | null;
  dayChangePct: number | null;
  tradeVolume: number | null;
  // облигации
  faceValue: number | null;
  couponRate: number | null;
  yieldToMaturity: number | null;
  duration: number | null;
  convexity: number | null;
  currentPrice: number | null;
  annualCoupon: number | null;
  couponFrequency: number | null;
  maturityDate: string | null;
  nextCouponDate: string | null;
  issueDate: string | null;
  issuerRating: string | null;
  reliabilityScore: number | null;
  investmentGrade: boolean | null;
  callable: boolean | null;
  puttable: boolean | null;
  suspiciousYield: boolean | null;
  accruedInterest: number | null;
  currentYield: number | null;
  offerDate: string | null;
  // акции
  lotSize: number | null;
  capitalization: number | null;
  sector: string | null;
  listLevel: number | null;
}

export interface Summary {
  count: number;
  averageYield: number;
  averageReliability: number;
  investmentGradeShare: number;
  maxYield: number;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  summary: Summary | null;
}

export interface Candle {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface InstrumentPayment {
  date: string;
  kind: "COUPON" | "AMORTIZATION" | "DIVIDEND";
  perUnit: number;
  currency: string;
  /** Доля выплаты от текущей цены бумаги, %. null — если цены нет. */
  sharePct: number | null;
}

export interface DividendInfo {
  trailingSum: number | null;
  dividendYield: number | null;
  count: number;
}

export interface InstrumentPerformance {
  lastPrice: number;
  change1d: number | null;
  change1w: number | null;
  change1m: number | null;
  change1y: number | null;
  changeYtd: number | null;
  high52w: number | null;
  low52w: number | null;
}

export interface InstrumentFilters {
  type: InstrumentType;
  query?: string;
  minYield?: number;
  maxDuration?: number;
  minReliability?: number;
  onlyInvestmentGrade?: boolean;
  excludeSuspicious?: boolean;
  sector?: string;
  page?: number;
  size?: number;
  sort?: string;
}
