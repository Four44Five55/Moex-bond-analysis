package org.example.application;

import org.example.application.portfolio.InstrumentPricing;
import org.example.domain.model.*;
import org.example.domain.portfolio.*;
import org.example.domain.portfolio.allocation.AllocationCalculator;
import org.example.domain.portfolio.allocation.AllocationDimension;
import org.example.domain.portfolio.allocation.AllocationDimensionFactory;
import org.example.domain.portfolio.allocation.Holding;
import org.example.domain.portfolio.calendar.PaymentCalendar;
import org.example.domain.portfolio.calendar.PaymentCalendarCalculator;
import org.example.domain.portfolio.calendar.PaymentCalendarEntry;
import org.example.domain.portfolio.calendar.PaymentCalendarMonth;
import org.example.domain.portfolio.cash.CashBalanceCalculator;
import org.example.domain.portfolio.deposit.DepositCalculator;
import org.example.domain.portfolio.deposit.DepositTaxBreakdown;
import org.example.domain.portfolio.deposit.DepositTaxCalculator;
import org.example.infrastructure.persistence.CashMovementRepository;
import org.example.domain.portfolio.history.CashFlow;
import org.example.domain.portfolio.history.DatedTrade;
import org.example.domain.portfolio.history.PortfolioValueCalculator;
import org.example.domain.portfolio.history.PortfolioValueRequest;
import org.example.domain.portfolio.history.ValuePoint;
import org.example.domain.portfolio.history.XirrCalculator;
import org.example.domain.portfolio.tax.TaxBreakdown;
import org.example.domain.portfolio.tax.TaxContext;
import org.example.domain.portfolio.tax.TaxRates;
import org.example.domain.portfolio.tax.TaxStrategyFactory;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.TransactionRepository;
import org.example.domain.port.CorporateActionsProvider;
import org.example.web.dto.portfolio.AccountLineDto;
import org.example.web.dto.portfolio.AllocationSliceDto;
import org.example.web.dto.portfolio.CalendarKindDto;
import org.example.web.dto.portfolio.CalendarMonthDto;
import org.example.web.dto.portfolio.ConsolidatedOverviewDto;
import org.springframework.beans.factory.annotation.Value;
import org.example.web.dto.portfolio.IncomeEventDto;
import org.example.web.dto.portfolio.PaymentCalendarDto;
import org.example.web.dto.portfolio.PortfolioHistoryDto;
import org.example.web.dto.portfolio.PortfolioOverviewDto;
import org.example.web.dto.portfolio.PortfolioSummaryDto;
import org.example.web.dto.portfolio.PositionDto;
import org.example.web.dto.portfolio.ValuePointDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-фасад портфеля: строит позиции (FIFO), P&L, прогноз доходов, налоги и сводку.
 * Одна когезивная точка входа для web-слоя — избегает циклов между сервисами.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int PROJECTION_MONTHS = 12;
    private static final int DEFAULT_HISTORY_MONTHS = 12;
    private static final int MAX_HISTORY_MONTHS = 120;
    /** Дальше горизонт календаря смысла не имеет: купоны известны до погашения, дивиденды — нет. */
    private static final int MAX_CALENDAR_MONTHS = 60;
    /** Код дневного интервала свечей MOEX. */
    private static final int DAILY_INTERVAL = 24;
    /**
     * Запас свечей до начала периода ряда стоимости: цена на дату берётся по ближайшему
     * предшествующему торговому дню, а новогодние каникулы длиннее недели.
     */
    private static final int PRICE_LOOKBACK_DAYS = 14;

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final CandleService candleService;
    private final FifoCalculator fifoCalculator;
    private final IncomeForecaster incomeForecaster;
    private final CorporateActionsProvider corporateActions;
    private final TaxStrategyFactory taxStrategyFactory;
    private final AllocationDimensionFactory allocationDimensionFactory;
    private final AllocationCalculator allocationCalculator;
    private final PortfolioValueCalculator portfolioValueCalculator;
    private final XirrCalculator xirrCalculator;
    private final CashMovementRepository cashMovementRepository;
    private final CashBalanceCalculator cashBalanceCalculator;
    private final DepositService depositService;
    private final DepositCalculator depositCalculator;
    private final DepositTaxCalculator depositTaxCalculator;
    private final PaymentCalendarCalculator paymentCalendarCalculator;
    private final KeyRateService keyRateService;

    public PortfolioService(AccountService accountService,
                            TransactionRepository transactionRepository,
                            InstrumentRepository instrumentRepository,
                            CandleService candleService,
                            FifoCalculator fifoCalculator,
                            IncomeForecaster incomeForecaster,
                            CorporateActionsProvider corporateActions,
                            TaxStrategyFactory taxStrategyFactory,
                            AllocationDimensionFactory allocationDimensionFactory,
                            AllocationCalculator allocationCalculator,
                            PortfolioValueCalculator portfolioValueCalculator,
                            XirrCalculator xirrCalculator,
                            CashMovementRepository cashMovementRepository,
                            CashBalanceCalculator cashBalanceCalculator,
                            DepositService depositService,
                            DepositCalculator depositCalculator,
                            DepositTaxCalculator depositTaxCalculator,
                            PaymentCalendarCalculator paymentCalendarCalculator,
                            KeyRateService keyRateService) {
        this.cashMovementRepository = cashMovementRepository;
        this.cashBalanceCalculator = cashBalanceCalculator;
        this.depositService = depositService;
        this.depositCalculator = depositCalculator;
        this.depositTaxCalculator = depositTaxCalculator;
        this.paymentCalendarCalculator = paymentCalendarCalculator;
        this.keyRateService = keyRateService;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.instrumentRepository = instrumentRepository;
        this.candleService = candleService;
        this.fifoCalculator = fifoCalculator;
        this.incomeForecaster = incomeForecaster;
        this.corporateActions = corporateActions;
        this.taxStrategyFactory = taxStrategyFactory;
        this.allocationDimensionFactory = allocationDimensionFactory;
        this.allocationCalculator = allocationCalculator;
        this.portfolioValueCalculator = portfolioValueCalculator;
        this.xirrCalculator = xirrCalculator;
    }

    private record PositionView(Instrument instrument, String secId, double quantity,
                                double openCost, double marketValue, double unrealizedPnl) {
    }

    /**
     * @param instruments   бумаги всех сделок счёта (не только открытых позиций) — держим в снапшоте,
     *                      чтобы расчёт полученных выплат не перечитывал их из БД второй раз
     * @param pricedTrades  сделки, приведённые к деньгам — нужны расчёту остатка наличных
     * @param cashMovements пополнения и выводы; пустой список означает, что учёт денег по счёту
     *                      не ведётся, и это меняет и остаток ({@link #freeCash}),
     *                      и способ расчёта доходности (см. {@link #capitalFlows})
     */
    private record Snapshot(Account account, List<Transaction> transactions,
                            Map<String, Instrument> instruments,
                            List<PricedTrade> pricedTrades,
                            List<CashMovement> cashMovements,
                            List<PositionView> positions,
                            List<RealizedSale> realizedSales, double totalCommission) {
    }

    // --- Публичные операции ---

    public List<PositionDto> positions(String clientId, Long accountId) {
        Snapshot s = snapshot(accountService.getOwned(clientId, accountId));
        return s.positions().stream().map(this::toDto).toList();
    }

    public List<IncomeEventDto> income(String clientId, Long accountId) {
        Account account = accountService.getOwned(clientId, accountId);
        Snapshot s = snapshot(account);
        boolean couponTaxable = isCouponTaxable(account);
        return rawIncomeEvents(s).stream()
                .map(e -> toIncomeDto(e, account, couponTaxable, s.instruments()))
                .toList();
    }

    public TaxBreakdown tax(String clientId, Long accountId) {
        Account account = accountService.getOwned(clientId, accountId);
        Snapshot s = snapshot(account);
        return computeTax(account, s.realizedSales(), projectedAnnualIncome(rawIncomeEvents(s)));
    }

    /** Фактически полученные выплаты: только даты в прошлом и только если на дату отсечки была позиция. */
    public List<IncomeEventDto> received(String clientId, Long accountId) {
        Account account = accountService.getOwned(clientId, accountId);
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(account.getId());
        Map<String, Instrument> instruments = loadInstruments(txns);
        return toReceivedDtos(realizedIncomeEvents(txns, instruments), account, instruments);
    }

    /**
     * Всё, что нужно странице счёта, из <b>одного</b> снапшота: сводка, позиции, прогноз выплат,
     * полученные выплаты и налог.
     * <p>
     * Раздельные вызовы {@code summary/positions/income/received/tax} дают тот же результат,
     * но каждый строит снапшот заново — это четыре прогона FIFO и четыре обхода выплат на одну
     * загрузку страницы. Здесь снапшот строится один раз, прогноз обходится один раз,
     * и его же переиспользует расчёт налога.
     */
    public PortfolioOverviewDto overview(String clientId, Long accountId) {
        Account account = accountService.getOwned(clientId, accountId);
        Snapshot s = snapshot(account);
        boolean couponTaxable = isCouponTaxable(account);

        List<IncomeEvent> forecast = rawIncomeEvents(s);
        double[] annual = projectedAnnualIncome(forecast);
        TaxBreakdown tax = computeTax(account, s.realizedSales(), annual);
        List<IncomeEvent> realized = realizedIncomeEvents(s.transactions(), s.instruments());

        return new PortfolioOverviewDto(
                summaryOf(s, annual, tax.totalTax(), realized),
                s.positions().stream().map(this::toDto).toList(),
                forecast.stream().map(e -> toIncomeDto(e, account, couponTaxable, s.instruments())).toList(),
                toReceivedDtos(realized, account, s.instruments()),
                tax);
    }

    /** Аллокация портфеля по измерению (тип/сектор/валюта) — доли по рыночной стоимости позиций. */
    public List<AllocationSliceDto> allocation(String clientId, Long accountId, String by) {
        Account account = accountService.getOwned(clientId, accountId);
        AllocationDimension dimension = allocationDimensionFactory.forKey(by);
        Snapshot s = snapshot(account);
        List<Holding> holdings = s.positions().stream()
                .map(p -> new Holding(dimension.categoryOf(p.instrument()), p.marketValue()))
                .toList();
        return toSlices(holdings);
    }

    private List<AllocationSliceDto> toSlices(List<Holding> holdings) {
        return allocationCalculator.breakdown(holdings).stream()
                .map(sl -> new AllocationSliceDto(sl.category(), sl.value(), sl.percent()))
                .toList();
    }

    /**
     * Сводка по всем счетам клиента: суммы, распределение капитала по счетам и по типам бумаг,
     * единый календарь ближайших выплат и общий XIRR.
     * <p>
     * Налог суммируется по счетам, а не считается от общей прибыли: ставка и льготы —
     * свойство счёта (у ИИС типа Б купоны вообще не облагаются), поэтому каждая стратегия
     * должна отработать на своих сделках. XIRR, наоборот, считается по объединённым потокам
     * всех счетов — это доходность капитала целиком, а не среднее из доходностей.
     */
    public ConsolidatedOverviewDto consolidated(String clientId) {
        List<Account> accounts = accountService.list(clientId);
        List<Deposit> deposits = depositService.entities(clientId);
        if (accounts.isEmpty() && deposits.isEmpty()) {
            return ConsolidatedOverviewDto.empty();
        }

        AllocationDimension byTypeDimension = allocationDimensionFactory.forKey("type");
        List<AccountLineDto> lines = new ArrayList<>();
        List<Holding> accountHoldings = new ArrayList<>();
        List<Holding> typeHoldings = new ArrayList<>();
        List<IncomeEventDto> upcoming = new ArrayList<>();
        List<CashFlow> flows = new ArrayList<>();
        Totals totals = new Totals();

        for (Account account : accounts) {
            Snapshot s = snapshot(account);
            boolean couponTaxable = isCouponTaxable(account);

            double accountInvested = s.positions().stream().mapToDouble(PositionView::openCost).sum();
            double accountValue = s.positions().stream().mapToDouble(PositionView::marketValue).sum();

            List<IncomeEvent> forecast = rawIncomeEvents(s);
            double[] annual = projectedAnnualIncome(forecast);
            List<IncomeEvent> realizedIncome = realizedIncomeEvents(s.transactions(), s.instruments());

            totals.invested += accountInvested;
            totals.marketValue += accountValue;
            totals.realizedPnl += s.realizedSales().stream().mapToDouble(RealizedSale::pnl).sum();
            totals.commission += s.totalCommission();
            totals.receivedIncome += realizedIncome.stream().mapToDouble(IncomeEvent::gross).sum();
            totals.projectedIncome += annual[0] + annual[1];
            totals.tax += computeTax(account, s.realizedSales(), annual).totalTax();
            totals.positions += s.positions().size();

            double accountCash = freeCash(s.cashMovements(), s.pricedTrades(), realizedIncome,
                    depositsFundedFrom(deposits, account.getId()));
            totals.cashBalance += accountCash;
            totals.addExternal(externalIn(s.cashMovements()), !s.transactions().isEmpty());

            lines.add(toAccountLine(account, s, accountInvested, accountValue, accountCash));
            // В разрезе по счетам показываем весь капитал счёта, включая непристроенные деньги
            double accountCapital = accountValue + Math.max(accountCash, 0);
            if (accountCapital > 0) {
                accountHoldings.add(new Holding(account.getName(), accountCapital));
            }
            for (PositionView p : s.positions()) {
                typeHoldings.add(new Holding(byTypeDimension.categoryOf(p.instrument()), p.marketValue()));
            }
            forecast.forEach(e -> upcoming.add(toIncomeDto(e, account, couponTaxable, s.instruments())));
            flows.addAll(capitalFlows(s.transactions(), s.instruments(), s.cashMovements()));
        }

        LocalDate today = LocalDate.now();
        double depositsValue = 0;
        for (Deposit deposit : deposits) {
            double value = depositCalculator.valueOn(deposit, today);
            depositsValue += value;
            if (value > 0) {
                accountHoldings.add(new Holding(deposit.getName(), value));
            }
            upcoming.addAll(upcomingInterest(deposit, today));
            flows.addAll(depositFlows(deposit, today));
            // Вклад с указанным счётом-источником открыт деньгами, которые уже посчитаны
            // пополнением этого счёта; внесённым извне считается только вклад со стороны
            if (deposit.getSourceAccountId() == null) {
                totals.addExternal(deposit.getPrincipal(), true);
            }
        }

        if (totals.cashBalance > 0) {
            typeHoldings.add(new Holding("Свободные деньги", totals.cashBalance));
        }
        if (depositsValue > 0) {
            typeHoldings.add(new Holding("Вклады", depositsValue));
        }

        double grandTotal = totals.totalValue() + depositsValue;
        if (grandTotal > 0) {
            flows.add(new CashFlow(today, grandTotal));
        }
        Double xirr = xirrCalculator.compute(flows);
        upcoming.sort(Comparator.comparing(IncomeEventDto::date));

        return new ConsolidatedOverviewDto(totals.toDto(grandTotal), lines,
                toSlices(accountHoldings), toSlices(typeHoldings), upcoming,
                xirr == null ? null : xirr * 100.0,
                depositService.list(clientId), depositsValue, grandTotal,
                depositTax(deposits, today));
    }

    /**
     * Календарь выплат по всему капиталу клиента: помесячные суммы за горизонт и сами выплаты.
     * <p>
     * Считается по тем же событиям, что и сводка, но горизонт здесь параметр, а не фиксированный
     * год: облигационная лестница видна только на нескольких годах вперёд. Месяцы без выплат
     * заполняются нулями — провал в календаре и есть то, ради чего его смотрят.
     * <p>
     * Дивиденды попадают сюда только объявленные, поэтому дальние месяцы у акций пустуют
     * не потому, что выплат не будет, а потому что их ещё не объявили ({@link IncomeForecaster}).
     *
     * @param months длина горизонта; приводится к диапазону 1..{@value #MAX_CALENDAR_MONTHS}
     */
    public PaymentCalendarDto calendar(String clientId, int months) {
        int horizon = Math.min(Math.max(months, 1), MAX_CALENDAR_MONTHS);
        List<Account> accounts = accountService.list(clientId);
        List<Deposit> deposits = depositService.entities(clientId);
        if (accounts.isEmpty() && deposits.isEmpty()) {
            return PaymentCalendarDto.empty(horizon);
        }

        LocalDate today = LocalDate.now();
        YearMonth firstMonth = YearMonth.from(today);
        LocalDate until = firstMonth.plusMonths(horizon).atDay(1); // первый день за горизонтом

        List<IncomeEventDto> payments = new ArrayList<>();
        for (Account account : accounts) {
            boolean couponTaxable = isCouponTaxable(account);
            Snapshot s = snapshot(account);
            rawIncomeEvents(s).stream()
                    .filter(e -> e.date().isBefore(until))
                    .map(e -> toIncomeDto(e, account, couponTaxable, s.instruments()))
                    .forEach(payments::add);
        }
        for (Deposit deposit : deposits) {
            upcomingInterest(deposit, today).stream()
                    .filter(e -> e.date().isBefore(until))
                    .forEach(payments::add);
        }
        payments.sort(Comparator.comparing(IncomeEventDto::date));

        List<PaymentCalendarEntry> entries = payments.stream()
                .map(p -> new PaymentCalendarEntry(p.date(), p.kind(), p.gross(), p.net()))
                .toList();
        PaymentCalendar calendar = paymentCalendarCalculator.build(entries, firstMonth, horizon);

        return new PaymentCalendarDto(
                calendar.months().stream().map(PortfolioService::toMonthDto).toList(),
                calendar.totalGross(), calendar.incomeGross(), calendar.principalReturn(),
                calendar.totalNet(), calendar.averageMonthlyNet(), payments, horizon);
    }

    private static CalendarMonthDto toMonthDto(PaymentCalendarMonth m) {
        return new CalendarMonthDto(m.month().toString(), m.gross(), m.net(), m.count(),
                m.byKind().stream()
                        .map(sl -> new CalendarKindDto(sl.kind(), sl.gross(), sl.net()))
                        .toList());
    }

    /**
     * Ближайшие поступления процентов по вкладу — на тех же правах, что купоны и дивиденды.
     * <p>
     * {@code net} равен {@code gross}: налог с процентов считается не с каждой выплаты, а от
     * годовой суммы по всем вкладам сразу (общий вычет), поэтому «на руки» по одной выплате
     * честно посчитать нельзя — эта величина отдельной строкой в {@code depositTax}.
     */
    private List<IncomeEventDto> upcomingInterest(Deposit deposit, LocalDate today) {
        return depositCalculator.payouts(deposit).stream()
                .filter(a -> !a.date().isBefore(today))
                .map(a -> new IncomeEventDto(a.date(), null, null, IncomeKind.INTEREST,
                        a.amount(), a.amount(), false, deposit.getName()))
                .toList();
    }

    /**
     * Потоки вклада для общего XIRR: открытие — отток тела, полученные проценты — приток.
     * <p>
     * У капитализируемого вклада выплат нет вовсе: проценты не покидают вклад и уже сидят
     * в его текущей стоимости, которая войдёт в завершающий поток. Учесть их ещё и потоком —
     * значит посчитать один и тот же доход дважды.
     * <p>
     * Тело вклада становится потоком, только если вклад открыт деньгами со стороны. Перевод
     * с брокерского счёта капиталом не является: те деньги уже вошли в потоки пополнением счёта,
     * а со счёта они ушли через {@link #freeCash} — иначе одна и та же сумма вычлась бы дважды
     * и доходность провалилась бы на ровном месте.
     */
    private List<CashFlow> depositFlows(Deposit deposit, LocalDate today) {
        List<CashFlow> flows = new ArrayList<>();
        if (deposit.getSourceAccountId() == null) {
            flows.add(new CashFlow(deposit.getOpenedOn(), -deposit.getPrincipal()));
        }
        if (!deposit.isCapitalized()) {
            depositCalculator.payouts(deposit).stream()
                    .filter(a -> !a.date().isAfter(today))
                    .forEach(a -> flows.add(new CashFlow(a.date(), a.amount())));
        }
        return flows;
    }

    /** Налог с процентов вкладов за текущий календарный год — вычет общий на все вклады клиента. */
    private DepositTaxBreakdown depositTax(List<Deposit> deposits, LocalDate today) {
        if (deposits.isEmpty()) {
            return DepositTaxBreakdown.zero();
        }
        LocalDate yearStart = today.withDayOfYear(1).minusDays(1);
        LocalDate yearEnd = today.withDayOfYear(today.lengthOfYear());
        double interest = deposits.stream()
                .mapToDouble(d -> depositCalculator.interestReceivedBetween(d, yearStart, yearEnd))
                .sum();
        return depositTaxCalculator.compute(interest, keyRateService.maxForYear(today.getYear()));
    }

    /** Накопитель сумм по счетам — иначе метод свёлся бы к десятку скачущих локальных переменных. */
    private static final class Totals {
        private double invested;
        private double marketValue;
        private double cashBalance;
        private double realizedPnl;
        private double commission;
        private double receivedIncome;
        private double projectedIncome;
        private double tax;
        private int positions;
        private double externalIn;
        /** Хоть один счёт с бумагами, но без учёта денег — и общая сумма внесённого уже не сумма. */
        private boolean externalComplete = true;

        /**
         * @param amount   внесено по этому источнику; {@code null} — учёт не ведётся
         * @param hasMoney есть ли по источнику вообще что считать: пустой счёт без сделок
         *                 и без движений итог не портит
         */
        private void addExternal(Double amount, boolean hasMoney) {
            if (amount != null) {
                externalIn += amount;
            } else if (hasMoney) {
                externalComplete = false;
            }
        }

        private double totalValue() {
            return marketValue + cashBalance;
        }

        /** @param grandTotal весь капитал клиента вместе с вкладами — прибыль считается от него */
        private PortfolioSummaryDto toDto(double grandTotal) {
            Double external = externalComplete ? externalIn : null;
            Double profit = external == null ? null : grandTotal - external;
            return new PortfolioSummaryDto(invested, marketValue, cashBalance, totalValue(),
                    external, profit,
                    marketValue - invested, realizedPnl,
                    commission, receivedIncome, projectedIncome, tax, positions);
        }
    }

    private AccountLineDto toAccountLine(Account account, Snapshot s, double invested,
                                         double marketValue, double cash) {
        double pnl = marketValue - invested;
        return new AccountLineDto(account.getId(), account.getName(), account.getType(),
                account.getIisType(), invested, marketValue, cash, marketValue + cash, pnl,
                invested > 0 ? pnl / invested * 100.0 : 0.0, s.positions().size());
    }

    /**
     * Дневной ряд стоимости портфеля за последние {@code months} месяцев и XIRR.
     * <p>
     * Ряд ограничен периодом, а денежные потоки для XIRR берутся за <b>всю</b> историю счёта:
     * доходность, посчитанная по обрезанной истории, не имеет смысла. Терминальным потоком
     * выступает текущая рыночная стоимость позиций.
     * <p>
     * Транзакция намеренно перекрывает read-only класса: {@link CandleService} догружает
     * недостающие свечи и сохраняет их, а в read-only транзакции запись отвергается.
     */
    @Transactional
    public PortfolioHistoryDto valueSeries(String clientId, Long accountId, int months) {
        Account account = accountService.getOwned(clientId, accountId);
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(account.getId());
        if (txns.isEmpty()) {
            return PortfolioHistoryDto.empty();
        }

        LocalDate to = LocalDate.now();
        LocalDate periodStart = to.minusMonths(clampMonths(months));
        LocalDate earliestTrade = txns.get(0).getTradeDate();
        LocalDate from = earliestTrade.isAfter(periodStart) ? earliestTrade : periodStart;
        if (from.isAfter(to)) {
            from = to; // сделки, датированные будущим — ряд вырождается в одну точку
        }

        Map<String, Instrument> instruments = instrumentRepository
                .findAllBySecIdIn(txns.stream().map(Transaction::getSecId).distinct().toList())
                .stream().collect(Collectors.toMap(Instrument::getSecId, i -> i));

        PortfolioValueRequest.Builder request = PortfolioValueRequest.builder(from, to)
                .trades(toDatedTrades(txns, instruments));
        for (var entry : instruments.entrySet()) {
            request.closes(entry.getKey(), dailyCloses(entry.getKey(), entry.getValue(), from, to));
        }

        List<ValuePoint> series = portfolioValueCalculator.series(request.build());

        // Ряд стоимости — только бумаги; свободные деньги входят в завершающий поток XIRR,
        // иначе доходность считалась бы от внесённых сумм, но капитал в кэше был бы потерян
        List<CashMovement> movements = cashMovementRepository
                .findByAccountIdOrderByMovedOnAscIdAsc(account.getId());
        double positionsValue = series.isEmpty() ? 0.0 : series.get(series.size() - 1).value();
        double terminal = positionsValue + freeCash(movements, pricedTrades(txns, instruments),
                realizedIncomeEvents(txns, instruments), fundedDeposits(account));
        Double xirr = computeXirr(capitalFlows(txns, instruments, movements), terminal, to);

        return new PortfolioHistoryDto(
                series.stream()
                        .map(p -> new ValuePointDto(p.date(), p.value(), p.invested(), p.pnl()))
                        .toList(),
                xirr == null ? null : xirr * 100.0);
    }

    private int clampMonths(int months) {
        return Math.min(Math.max(months, 1), MAX_HISTORY_MONTHS);
    }

    private List<DatedTrade> toDatedTrades(List<Transaction> txns, Map<String, Instrument> instruments) {
        return txns.stream()
                .map(t -> new DatedTrade(t.getSecId(), t.getSide(), t.getTradeDate(), t.getQuantity(),
                        InstrumentPricing.tradeMoneyPerUnit(instruments.get(t.getSecId()),
                                t.getPrice(), t.getAci()),
                        t.getCommission()))
                .toList();
    }

    /**
     * Дневные цены закрытия, приведённые к деньгам за бумагу.
     * <p>
     * Свечи запрашиваются с запасом до начала периода: цена берётся по ближайшему предшествующему
     * торговому дню, а первый день ряда легко приходится на выходные или праздники. Без запаса
     * для этих дат не находилось ни одной цены, и ряд начинался с нулевой стоимости —
     * график падал в ноль на старте.
     * <p>
     * Если истории нет вовсе (неликвид или сбой источника), позиция оценивается последней
     * известной ценой на всём периоде. Это ровная линия вместо динамики, но альтернатива —
     * ноль в стоимости при живой сумме во «вложено», то есть портфель в минус на всю бумагу.
     */
    private Map<LocalDate, Double> dailyCloses(String secId, Instrument instrument,
                                               LocalDate from, LocalDate to) {
        LocalDate priceFrom = from.minusDays(PRICE_LOOKBACK_DAYS);
        Map<LocalDate, Double> closes = new HashMap<>();
        try {
            for (var candle : candleService.getCandles(secId, DAILY_INTERVAL, priceFrom, to)) {
                LocalDate date = Instant.ofEpochSecond(candle.time())
                        .atZone(ZoneOffset.UTC).toLocalDate();
                closes.put(date, InstrumentPricing.moneyPerUnit(instrument, candle.close()));
            }
        } catch (RuntimeException e) {
            log.warn("История цен по {} недоступна: {}", secId, e.getMessage());
        }
        if (closes.isEmpty()) {
            double lastKnown = InstrumentPricing.currentMoneyPerUnit(instrument);
            if (lastKnown > 0) {
                log.warn("Свечей по {} нет, в ряду стоимости бумага пойдёт по последней цене", secId);
                closes.put(priceFrom, lastKnown);
            }
        }
        return closes;
    }

    private Double computeXirr(List<CashFlow> baseFlows, double terminalValue, LocalDate to) {
        List<CashFlow> flows = new ArrayList<>(baseFlows);
        if (terminalValue > 0) {
            flows.add(new CashFlow(to, terminalValue));
        }
        return xirrCalculator.compute(flows);
    }

    /**
     * Потоки капитала для XIRR — то, что инвестор реально вложил и забрал.
     * <p>
     * Если по счёту ведётся учёт денег, это пополнения (со знаком минус — капитал ушёл в портфель)
     * и выводы (плюс). Покупка бумаги на уже заведённые деньги капиталом не является: это
     * перекладывание внутри портфеля, и включать её в потоки нельзя — иначе доходность
     * растёт от одного лишь оборота.
     * <p>
     * Если движений денег нет — а так выглядит любой счёт, заведённый до появления их учёта, —
     * остаётся прежнее поведение: потоки по сделкам. Иначе у таких счетов XIRR просто исчез бы
     * (потоки одного знака корня не имеют).
     */
    private List<CashFlow> capitalFlows(List<Transaction> txns, Map<String, Instrument> instruments,
                                        List<CashMovement> movements) {
        if (movements.isEmpty()) {
            return tradeAndIncomeFlows(txns, instruments);
        }
        return movements.stream()
                .map(m -> new CashFlow(m.getMovedOn(), -m.signedAmount()))
                .toList();
    }

    /**
     * Свободные деньги счёта — или 0, если учёт денег по счёту не ведётся
     * (см. {@link CashBalanceCalculator#reportedBalance}).
     *
     * @param fundedDeposits тело вкладов, открытых деньгами этого счёта: они со счёта ушли
     *                       и теперь лежат во вкладе, где их и показывает сводка
     */
    private double freeCash(List<CashMovement> movements, List<PricedTrade> trades,
                            List<IncomeEvent> receivedIncome, double fundedDeposits) {
        double externalNet = movements.stream().mapToDouble(CashMovement::signedAmount).sum();
        double received = receivedIncome.stream().mapToDouble(IncomeEvent::gross).sum();
        double balance = cashBalanceCalculator
                .reportedBalance(!movements.isEmpty(), externalNet, trades, received);
        return movements.isEmpty() ? balance : balance - fundedDeposits;
    }

    /**
     * Тело вкладов, открытых деньгами этого счёта. Отдельный запрос — вклады привязаны к клиенту,
     * а не к счёту, и в снапшот счёта они не попадают.
     */
    private double fundedDeposits(Account account) {
        return depositsFundedFrom(depositService.entities(account.getClientId()), account.getId());
    }

    /** Тело вкладов, открытых деньгами конкретного счёта. */
    private static double depositsFundedFrom(List<Deposit> deposits, Long accountId) {
        return deposits.stream()
                .filter(d -> accountId.equals(d.getSourceAccountId()))
                .mapToDouble(Deposit::getPrincipal)
                .sum();
    }

    private List<PricedTrade> pricedTrades(List<Transaction> txns, Map<String, Instrument> instruments) {
        return txns.stream()
                .map(t -> new PricedTrade(t.getSide(), t.getTradeDate(), t.getQuantity(),
                        InstrumentPricing.tradeMoneyPerUnit(instruments.get(t.getSecId()),
                                t.getPrice(), t.getAci()),
                        t.getCommission()))
                .toList();
    }

    /**
     * Потоки счёта без завершающего: покупки — со знаком минус (с комиссией), продажи — плюс
     * (за вычетом комиссии), полученные купоны и дивиденды — плюс. Завершающий поток
     * (текущая стоимость) добавляет вызывающий: для одного счёта это конец ряда стоимости,
     * для сводки по всем счетам — суммарная рыночная стоимость.
     */
    private List<CashFlow> tradeAndIncomeFlows(List<Transaction> txns,
                                               Map<String, Instrument> instruments) {
        List<CashFlow> flows = new ArrayList<>();
        for (Transaction t : txns) {
            double money = t.getQuantity() * InstrumentPricing.tradeMoneyPerUnit(
                    instruments.get(t.getSecId()), t.getPrice(), t.getAci());
            flows.add(new CashFlow(t.getTradeDate(), t.getSide() == TradeSide.BUY
                    ? -(money + t.getCommission())
                    : money - t.getCommission()));
        }
        realizedIncomeEvents(txns, instruments).forEach(e -> flows.add(new CashFlow(e.date(), e.gross())));
        return flows;
    }

    public PortfolioSummaryDto summary(String clientId, Long accountId) {
        Account account = accountService.getOwned(clientId, accountId);
        Snapshot s = snapshot(account);

        // Прогноз считается один раз и переиспользуется в налоге — обход выплат ходит во внешний источник
        double[] annual = projectedAnnualIncome(rawIncomeEvents(s));
        double estimatedTax = computeTax(account, s.realizedSales(), annual).totalTax();
        return summaryOf(s, annual, estimatedTax,
                realizedIncomeEvents(s.transactions(), s.instruments()));
    }

    private PortfolioSummaryDto summaryOf(Snapshot s, double[] annual, double estimatedTax,
                                          List<IncomeEvent> realizedIncome) {
        double invested = s.positions().stream().mapToDouble(PositionView::openCost).sum();
        double marketValue = s.positions().stream().mapToDouble(PositionView::marketValue).sum();
        double realized = s.realizedSales().stream().mapToDouble(RealizedSale::pnl).sum();
        double receivedIncome = realizedIncome.stream().mapToDouble(IncomeEvent::gross).sum();
        double cash = freeCash(s.cashMovements(), s.pricedTrades(), realizedIncome,
                fundedDeposits(s.account()));
        double totalValue = marketValue + cash;

        Double externalIn = externalIn(s.cashMovements());
        return new PortfolioSummaryDto(invested, marketValue, cash, totalValue,
                externalIn, profitOver(totalValue, externalIn),
                marketValue - invested, realized,
                s.totalCommission(), receivedIncome, annual[0] + annual[1], estimatedTax,
                s.positions().size());
    }

    /**
     * Внесено извне: пополнения минус выводы. {@code null}, если движений нет вовсе — тогда
     * неизвестно не «ноль», а именно «неизвестно», и прибыль от такой базы считать нельзя.
     */
    private static Double externalIn(List<CashMovement> movements) {
        return movements.isEmpty() ? null
                : movements.stream().mapToDouble(CashMovement::signedAmount).sum();
    }

    /**
     * Заработано за всё время = капитал сейчас − внесено извне.
     * <p>
     * Единственный способ сложить рост цены, полученные выплаты и результат продаж, не считая
     * ничего дважды: реинвестированный купон уже сидит и в себестоимости позиций, и в их
     * рыночной стоимости, поэтому «вложено + получено» капиталом не является.
     */
    private static Double profitOver(double totalValue, Double externalIn) {
        return externalIn == null ? null : totalValue - externalIn;
    }

    // --- Ядро расчёта ---

    private Snapshot snapshot(Account account) {
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(account.getId());
        List<CashMovement> movements = cashMovementRepository
                .findByAccountIdOrderByMovedOnAscIdAsc(account.getId());
        double totalCommission = txns.stream().mapToDouble(Transaction::getCommission).sum();

        Map<String, List<Transaction>> bySec = groupBySecId(txns);
        Map<String, Instrument> instruments = loadInstruments(txns);

        List<PositionView> positions = new ArrayList<>();
        List<RealizedSale> realized = new ArrayList<>();

        for (var entry : bySec.entrySet()) {
            Instrument inst = instruments.get(entry.getKey());
            List<PricedTrade> priced = entry.getValue().stream()
                    .map(t -> new PricedTrade(t.getSide(), t.getTradeDate(), t.getQuantity(),
                            InstrumentPricing.tradeMoneyPerUnit(inst, t.getPrice(), t.getAci()),
                            t.getCommission()))
                    .toList();

            FifoResult fifo = fifoCalculator.process(priced);
            realized.addAll(fifo.realizedSales());

            double qty = fifo.openQuantity();
            if (qty > 1e-9) {
                double openCost = fifo.openCost();
                // Грязная цена: себестоимость лота уже содержит уплаченный НКД
                double marketValue = InstrumentPricing.currentPositionMoneyPerUnit(inst) * qty;
                positions.add(new PositionView(inst, entry.getKey(), qty, openCost,
                        marketValue, marketValue - openCost));
            }
        }
        return new Snapshot(account, txns, instruments, pricedTrades(txns, instruments), movements,
                positions, realized, totalCommission);
    }

    private Map<String, List<Transaction>> groupBySecId(List<Transaction> txns) {
        return txns.stream()
                .collect(Collectors.groupingBy(Transaction::getSecId, LinkedHashMap::new, Collectors.toList()));
    }

    /** Бумаги всех сделок, по коду. Единственное место, где инструменты счёта читаются из БД. */
    private Map<String, Instrument> loadInstruments(List<Transaction> txns) {
        List<String> secIds = txns.stream().map(Transaction::getSecId).distinct().toList();
        return instrumentRepository.findAllBySecIdIn(secIds).stream()
                .collect(Collectors.toMap(Instrument::getSecId, i -> i));
    }

    /**
     * Реально полученные выплаты по истории сделок: событие в прошлом × позиция на дату отсечки.
     * Карта инструментов передаётся вызывающим — он её уже загрузил.
     */
    private List<IncomeEvent> realizedIncomeEvents(List<Transaction> txns,
                                                   Map<String, Instrument> instruments) {
        Map<String, List<Transaction>> bySec = groupBySecId(txns);
        LocalDate today = LocalDate.now();
        List<IncomeEvent> events = new ArrayList<>();
        for (var entry : bySec.entrySet()) {
            String secId = entry.getKey();
            Instrument inst = instruments.get(secId);
            List<Transaction> ts = entry.getValue();
            try {
                if (inst instanceof Bond) {
                    for (var c : corporateActions.coupons(secId)) {
                        addRealized(events, secId, IncomeKind.COUPON, c.date(), c.perBond(), ts, today);
                    }
                    for (var a : corporateActions.amortizations(secId)) {
                        addRealized(events, secId, IncomeKind.AMORTIZATION, a.date(), a.perBond(), ts, today);
                    }
                } else if (inst instanceof Share) {
                    for (var d : corporateActions.dividends(secId)) {
                        addRealized(events, secId, IncomeKind.DIVIDEND, d.recordDate(), d.perShare(), ts, today);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("История выплат по {} пропущена: {}", secId, e.getMessage());
            }
        }
        return events;
    }

    private void addRealized(List<IncomeEvent> events, String secId, IncomeKind kind,
                             LocalDate date, double perUnit, List<Transaction> txns, LocalDate today) {
        if (date == null || !date.isBefore(today)) {
            return; // только прошедшие выплаты
        }
        double qty = quantityAsOf(txns, date);
        if (qty > 1e-9) {
            events.add(new IncomeEvent(date, secId, kind, perUnit * qty, false));
        }
    }

    /** Количество бумаг на конец дня {@code date} по истории сделок (BUY − SELL, tradeDate ≤ date). */
    private double quantityAsOf(List<Transaction> txns, LocalDate date) {
        double qty = 0;
        for (Transaction t : txns) {
            if (!t.getTradeDate().isAfter(date)) {
                qty += t.getSide() == TradeSide.BUY ? t.getQuantity() : -t.getQuantity();
            }
        }
        return qty;
    }

    private List<IncomeEvent> rawIncomeEvents(Snapshot s) {
        LocalDate today = LocalDate.now();
        List<IncomeEvent> events = new ArrayList<>();
        for (PositionView p : s.positions()) {
            // Сбой внешнего источника по одной бумаге не должен ронять весь расчёт
            try {
                if (p.instrument() instanceof Bond) {
                    events.addAll(incomeForecaster.bondIncome(p.secId(), p.quantity(),
                            corporateActions.coupons(p.secId()),
                            corporateActions.amortizations(p.secId()), today));
                } else if (p.instrument() instanceof Share) {
                    events.addAll(incomeForecaster.dividendIncome(p.secId(), p.quantity(),
                            corporateActions.dividends(p.secId()), today));
                }
            } catch (RuntimeException e) {
                log.warn("Прогноз дохода по {} пропущен: {}", p.secId(), e.getMessage());
            }
        }
        events.sort(Comparator.comparing(IncomeEvent::date));
        return events;
    }

    /** @return [couponGross, dividendGross] за ближайшие 12 месяцев (амортизации не входят в доход). */
    private double[] projectedAnnualIncome(List<IncomeEvent> events) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusMonths(PROJECTION_MONTHS);
        double coupon = 0, dividend = 0;
        for (IncomeEvent e : events) {
            if (e.date().isBefore(from) || e.date().isAfter(to)) {
                continue;
            }
            if (e.kind() == IncomeKind.COUPON) {
                coupon += e.gross();
            } else if (e.kind() == IncomeKind.DIVIDEND) {
                dividend += e.gross();
            }
        }
        return new double[]{coupon, dividend};
    }

    /** @param annual [couponGross, dividendGross] — считается вызывающим, чтобы не обходить выплаты дважды. */
    private TaxBreakdown computeTax(Account account, List<RealizedSale> sales, double[] annual) {
        TaxContext ctx = new TaxContext(account, sales, annual[0], annual[1]);
        return taxStrategyFactory.forAccount(account).compute(ctx);
    }

    // --- Мапперы ---

    private PositionDto toDto(PositionView p) {
        double avgCost = p.quantity() > 0 ? p.openCost() / p.quantity() : 0;
        double currentPrice = p.instrument() != null && p.instrument().getLastPrice() != null
                ? p.instrument().getLastPrice() : 0;
        double unrealizedPct = p.openCost() > 0 ? p.unrealizedPnl() / p.openCost() * 100.0 : 0;
        InstrumentType type = p.instrument() != null ? p.instrument().getType() : null;
        String name = p.instrument() != null ? p.instrument().getShortName() : p.secId();
        return new PositionDto(p.secId(), type, name, p.quantity(), avgCost, p.openCost(),
                currentPrice, p.marketValue(), p.unrealizedPnl(), unrealizedPct);
    }

    /** Полученные выплаты — новыми вперёд. */
    private List<IncomeEventDto> toReceivedDtos(List<IncomeEvent> events, Account account,
                                                Map<String, Instrument> instruments) {
        boolean couponTaxable = isCouponTaxable(account);
        return events.stream()
                .sorted(Comparator.comparing(IncomeEvent::date).reversed())
                .map(e -> toIncomeDto(e, account, couponTaxable, instruments))
                .toList();
    }

    /** Название бумаги для списков выплат; код без названия читается плохо, но и падать из-за него незачем. */
    private static String nameOf(Map<String, Instrument> instruments, String secId) {
        Instrument instrument = secId == null ? null : instruments.get(secId);
        return instrument == null ? null : instrument.getShortName();
    }

    private IncomeEventDto toIncomeDto(IncomeEvent e, Account account, boolean couponTaxable,
                                       Map<String, Instrument> instruments) {
        double net = switch (e.kind()) {
            case AMORTIZATION -> e.gross();  // возврат тела долга, не доход
            // Проценты вкладов сюда не попадают (у них свой путь), но ветка обязана существовать:
            // налог по ним считается от годовой суммы всех вкладов, а не с отдельной выплаты
            case INTEREST -> e.gross();
            case COUPON -> couponTaxable ? e.gross() * (1 - account.getBaseTaxRate() / 100.0) : e.gross();
            case DIVIDEND -> e.gross() * (1 - TaxRates.DIVIDEND_PCT / 100.0);
        };
        return new IncomeEventDto(e.date(), e.secId(), nameOf(instruments, e.secId()), e.kind(),
                e.gross(), net, e.estimate(), account.getName());
    }

    private boolean isCouponTaxable(Account account) {
        return !(account.getType() == AccountType.IIS && account.getIisType() == IisType.B);
    }
}
