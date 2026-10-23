export type AccountType = "BROKERAGE" | "IIS";
export type IisType = "A" | "B";
export type TradeSide = "BUY" | "SELL";
export type IncomeKind = "COUPON" | "AMORTIZATION" | "DIVIDEND" | "INTEREST" | "RECOVERY";
export type PayoutPeriod = "MONTHLY" | "QUARTERLY" | "SEMIANNUAL" | "ANNUAL" | "AT_MATURITY";

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
  /** Краткое название бумаги; null — инструмента уже нет в справочнике. */
  secName: string | null;
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
  /** DEFAULT — выплат не ждём, TECHNICAL — биржа видела просрочку, null — бумага платит. */
  defaultStatus: "DEFAULT" | "TECHNICAL" | null;
  /** С этой даты платежи графика не считаются полученными. */
  paymentsStoppedOn: string | null;
  /** Дату назвал инвестор; false — приложение подставило «сегодня» по признаку MOEX. */
  defaultConfirmed: boolean;
}

/** Возмещение по бумаге в дефолте: реструктуризация, банкротство — суммой на позицию. */
export interface Recovery {
  id: number;
  secId: string;
  receivedOn: string;
  amount: number;
  note: string | null;
}

export interface RecoveryRequest {
  secId: string;
  receivedOn: string;
  amount: number;
  note?: string | null;
}

/**
 * Потери от дефолта. Две группы, которые нельзя складывать: деньги инвестора
 * (вложено → возвращено → потеряно) и долг эмитента по графику (недополучено).
 */
export interface DefaultLoss {
  quantity: number;
  invested: number;
  saleProceeds: number;
  couponsReceived: number;
  principalReceived: number;
  recovered: number;
  marketValue: number;
  missedCoupons: number;
  missedPrincipal: number;
  futureCoupons: number;
  futurePrincipal: number;
  capitalReturned: number;
  capitalLoss: number;
  netResult: number;
  recoveryRatePct: number;
}

export interface DefaultPosition {
  accountId: number;
  accountName: string;
  secId: string;
  name: string;
  moexDefault: boolean;
  moexTechnicalDefault: boolean;
  paymentsStoppedOn: string;
  confirmed: boolean;
  note: string | null;
  loss: DefaultLoss;
}

export interface DefaultReport {
  positions: DefaultPosition[];
  totals: DefaultLoss;
}

export type CashDirection = "IN" | "OUT";

export interface CashMovement {
  id: number;
  direction: CashDirection;
  movedOn: string;
  amount: number;
  note: string | null;
}

export interface CashMovementRequest {
  direction: CashDirection;
  movedOn: string;
  amount: number;
  note?: string | null;
}

export interface PortfolioSummary {
  /** Себестоимость открытых позиций — включая купленное на реинвестированные выплаты. */
  invested: number;
  marketValue: number;
  /** Свободные деньги; ноль, если движения денег не заведены. */
  cashBalance: number;
  /** Бумаги по рынку плюс свободные деньги. */
  totalValue: number;
  /** Внесено извне: пополнения − выводы (+ тело вкладов в сводке). null — учёт не ведётся. */
  externalIn: number | null;
  /** Заработано за всё время = капитал − внесённое. null, если внесённое неизвестно. */
  totalProfit: number | null;
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
  /** null для процентов по вкладу — бумаги там нет. */
  secId: string | null;
  /** Краткое название бумаги: по коду вида RU000A10B2N2 не понять, чья это выплата. */
  secName: string | null;
  kind: IncomeKind;
  gross: number;
  net: number;
  estimate: boolean;
  /** Счёт, куда придёт выплата — нужен в сводном календаре по всем счетам. */
  accountName: string;
}

export interface AccountLine {
  id: number;
  name: string;
  type: AccountType;
  iisType: IisType | null;
  invested: number;
  marketValue: number;
  cashBalance: number;
  totalValue: number;
  unrealizedPnl: number;
  unrealizedPct: number;
  positionsCount: number;
}

export interface Deposit {
  id: number;
  name: string;
  principal: number;
  ratePct: number;
  openedOn: string;
  maturesOn: string;
  capitalized: boolean;
  payoutPeriod: PayoutPeriod;
  note: string | null;
  /** Тело плюс начисленные, но ещё не выплаченные проценты. */
  currentValue: number;
  accruedNotPaid: number;
  totalInterest: number;
  nextPayoutOn: string | null;
  matured: boolean;
  /** Счёт, с которого сняты деньги; null — вклад открыт деньгами со стороны. */
  sourceAccountId: number | null;
  sourceAccountName: string | null;
}

export interface DepositRequest {
  name: string;
  principal: number;
  ratePct: number;
  openedOn: string;
  maturesOn: string;
  capitalized: boolean;
  payoutPeriod: PayoutPeriod;
  note?: string | null;
  /** Счёт-источник денег; null — вклад открыт деньгами со стороны. */
  sourceAccountId?: number | null;
}

/** Налог с процентов по вкладам: вычет один на все вклады клиента, а не на каждый. */
export interface DepositTaxBreakdown {
  interestIncome: number;
  deduction: number;
  taxableIncome: number;
  tax: number;
  /** Максимальная ключевая ставка ЦБ за год — из неё посчитан вычет. */
  keyRatePct: number;
  notes: string;
}

/** Сводка по всем счетам и вкладам сразу — страница «Портфель». */
export interface ConsolidatedOverview {
  totals: PortfolioSummary;
  accounts: AccountLine[];
  byAccount: AllocationSlice[];
  byType: AllocationSlice[];
  upcomingIncome: IncomeEvent[];
  xirrPct: number | null;
  deposits: Deposit[];
  depositsValue: number;
  /** Бумаги + свободные деньги + вклады. */
  grandTotal: number;
  depositTax: DepositTaxBreakdown;
}

/** Итог дозаполнения НКД в ранее заведённых сделках. */
export interface AciBackfill {
  /** true — это была примерка, ничего не сохранено. */
  dryRun: boolean;
  /** Сделки по облигациям с незаполненным НКД. */
  candidates: number;
  filled: number;
  /** По ним биржа НКД не назвала — остались как были. */
  unknown: number;
  /** На столько вырастет стоимость этих сделок. */
  costDelta: number;
  rows: Array<{
    txId: number;
    secId: string;
    secName: string | null;
    tradeDate: string;
    side: TradeSide;
    quantity: number;
    aci: number;
    quotedOn: string;
    amount: number;
  }>;
}

/** Месяц календаря выплат; месяцы без выплат приходят с нулями — провал тоже информация. */
export interface CalendarMonth {
  /** Формат `YYYY-MM`. */
  month: string;
  gross: number;
  net: number;
  count: number;
  byKind: Array<{ kind: IncomeKind; gross: number; net: number }>;
}

/** Календарь выплат по всему капиталу: помесячные суммы плюс сами выплаты за горизонт. */
export interface PaymentCalendar {
  months: CalendarMonth[];
  /** Всё, что поступит за горизонт: прогноз будущих выплат, а не начисленное. */
  totalGross: number;
  /** Из него доход — без возврата номинала по амортизациям. */
  incomeGross: number;
  /** Возврат вложенного номинала: деньги придут, но доходом не являются. */
  principalReturn: number;
  totalNet: number;
  /** «На руки» в среднем за месяц: делится на весь горизонт, включая пустые месяцы. */
  averageMonthlyNet: number;
  payments: IncomeEvent[];
  horizonMonths: number;
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
