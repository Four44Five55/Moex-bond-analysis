import { apiGetWithClient, apiPost, apiDelete } from "../../shared/api/client";
import type {
  Account,
  AccountRequest,
  AddTransactionRequest,
  AllocationDimension,
  AllocationSlice,
  IncomeEvent,
  PortfolioHistory,
  PortfolioOverview,
  PortfolioSummary,
  Position,
  TaxBreakdown,
  Transaction,
} from "./types";

const A = "/accounts";

export const portfolioApi = {
  listAccounts: () => apiGetWithClient<Account[]>(A),
  getAccount: (id: number) => apiGetWithClient<Account>(`${A}/${id}`),
  createAccount: (req: AccountRequest) => apiPost<Account>(A, req, true),
  deleteAccount: (id: number) => apiDelete<void>(`${A}/${id}`, true),

  listTransactions: (id: number) => apiGetWithClient<Transaction[]>(`${A}/${id}/transactions`),
  addTransaction: (id: number, req: AddTransactionRequest) =>
    apiPost<Transaction>(`${A}/${id}/transactions`, req, true),
  deleteTransaction: (id: number, txId: number) =>
    apiDelete<void>(`${A}/${id}/transactions/${txId}`, true),

  /** Страница счёта целиком — вместо пяти запросов, каждый из которых пересчитывал портфель заново. */
  overview: (id: number) => apiGetWithClient<PortfolioOverview>(`${A}/${id}/overview`),
  positions: (id: number) => apiGetWithClient<Position[]>(`${A}/${id}/positions`),
  summary: (id: number) => apiGetWithClient<PortfolioSummary>(`${A}/${id}/summary`),
  allocation: (id: number, by: AllocationDimension) =>
    apiGetWithClient<AllocationSlice[]>(`${A}/${id}/allocation?by=${by}`),
  valueSeries: (id: number, months: number) =>
    apiGetWithClient<PortfolioHistory>(`${A}/${id}/value-series?months=${months}`),
  income: (id: number) => apiGetWithClient<IncomeEvent[]>(`${A}/${id}/income`),
  received: (id: number) => apiGetWithClient<IncomeEvent[]>(`${A}/${id}/received`),
  tax: (id: number) => apiGetWithClient<TaxBreakdown>(`${A}/${id}/tax`),
};
