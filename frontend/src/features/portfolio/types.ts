export type AccountType = "BROKERAGE" | "IIS";
export type IisType = "A" | "B";
export type TradeSide = "BUY" | "SELL";
export type IncomeKind = "COUPON" | "AMORTIZATION" | "DIVIDEND";

export interface Account {
  id: number;
  name: string;
  type: AccountType;
  iisType: IisType | null;
  defaultCommissionPct: number;
  baseTaxRate: number;
  createdAt: string;
}

export interface AccountRequest {
  name: string;
  type: AccountType;
  iisType: IisType | null;
  defaultCommissionPct: number;
  baseTaxRate: number;
}

export interface Transaction {
  id: number;
  secId: string;
  side: TradeSide;
  tradeDate: string;
  quantity: number;
  price: number;
  aci: number;
  commission: number;
  note: string | null;
}

export interface AddTransactionRequest {
  secId: string;
  side: TradeSide;
  tradeDate: string;
  quantity: number;
  price: number;
  aci: number;
  commission?: number | null;
  note?: string | null;
}

export interface Position {
  secId: string;
  type: "BOND" | "SHARE" | null;
  name: string;
  quantity: number;
  avgCost: number;
  invested: number;
  currentPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPct: number;
}

export interface PortfolioSummary {
  invested: number;
  marketValue: number;
  unrealizedPnl: number;
  realizedPnl: number;
  totalCommission: number;
  receivedIncome: number;
  projectedAnnualIncome: number;
  estimatedTax: number;
  positionsCount: number;
}

export interface ValuePoint {
  date: string;
  value: number;
  invested: number;
  pnl: number;
}

export interface PortfolioHistory {
  points: ValuePoint[];
  /** null, если доходность не определена: счёт только пополнялся и ничего не возвращал. */
  xirrPct: number | null;
}

export type AllocationDimension = "type" | "sector" | "currency";

export interface AllocationSlice {
  category: string;
  value: number;
  percent: number;
}

export interface IncomeEvent {
  date: string;
  secId: string;
  kind: IncomeKind;
  gross: number;
  net: number;
  estimate: boolean;
}

export interface TaxBreakdown {
  realizedPnl: number;
  ldvExemptGain: number;
  taxableGain: number;
  gainTax: number;
  couponIncome: number;
  couponTax: number;
  dividendIncome: number;
  dividendTax: number;
  totalTax: number;
  notes: string;
}

/** Содержимое страницы счёта одним ответом: сервер строит снапшот портфеля один раз, а не на панель. */
export interface PortfolioOverview {
  summary: PortfolioSummary;
  positions: Position[];
  income: IncomeEvent[];
  received: IncomeEvent[];
  tax: TaxBreakdown;
}
