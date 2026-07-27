import { apiGetWithClient, apiPost, apiDelete } from "../../shared/api/client";
import type {
  AciBackfill,
  Account,
  AccountRequest,
  AddTransactionRequest,
  AllocationDimension,
  AllocationSlice,
  CashMovement,
  CashMovementRequest,
  ConsolidatedOverview,
  Deposit,
  DepositRequest,
  IncomeEvent,
  PaymentCalendar,
  PortfolioHistory,
  PortfolioOverview,
  PortfolioSummary,
  Position,
  TaxBreakdown,
  Transaction,
} from "./types";

const A = "/accounts";
const D = "/deposits";

export const portfolioApi = {
  /** Сводка по всем счетам сразу. Отдельный путь, чтобы не конкурировать с `/accounts/{id}`. */
  consolidated: () => apiGetWithClient<ConsolidatedOverview>("/portfolio/overview"),

  /** Календарь выплат по всем счетам и вкладам на months месяцев вперёд. */
  calendar: (months: number) =>
    apiGetWithClient<PaymentCalendar>(`/portfolio/calendar?months=${months}`),

  listAccounts: () => apiGetWithClient<Account[]>(A),
  getAccount: (id: number) => apiGetWithClient<Account>(`${A}/${id}`),
  createAccount: (req: AccountRequest) => apiPost<Account>(A, req, true),
  deleteAccount: (id: number) => apiDelete<void>(`${A}/${id}`, true),

  listTransactions: (id: number) => apiGetWithClient<Transaction[]>(`${A}/${id}/transactions`),
  addTransaction: (id: number, req: AddTransactionRequest) =>
    apiPost<Transaction>(`${A}/${id}/transactions`, req, true),
  deleteTransaction: (id: number, txId: number) =>
    apiDelete<void>(`${A}/${id}/transactions/${txId}`, true),

  /** Дозаполнить НКД в старых сделках. dryRun=true — только примерка, без записи. */
  backfillAci: (id: number, dryRun: boolean) =>
    apiPost<AciBackfill>(`${A}/${id}/transactions/backfill-aci?dryRun=${dryRun}`, undefined, true),

  /** Страница счёта целиком — вместо пяти запросов, каждый из которых пересчитывал портфель заново. */
  overview: (id: number) => apiGetWithClient<PortfolioOverview>(`${A}/${id}/overview`),
  listDeposits: () => apiGetWithClient<Deposit[]>(D),
  createDeposit: (req: DepositRequest) => apiPost<Deposit>(D, req, true),
  deleteDeposit: (id: number) => apiDelete<void>(`${D}/${id}`, true),

  listCash: (id: number) => apiGetWithClient<CashMovement[]>(`${A}/${id}/cash`),
  addCash: (id: number, req: CashMovementRequest) =>
    apiPost<CashMovement>(`${A}/${id}/cash`, req, true),
  deleteCash: (id: number, movementId: number) =>
    apiDelete<void>(`${A}/${id}/cash/${movementId}`, true),

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
