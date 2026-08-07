package org.example.application;

import org.example.application.portfolio.InstrumentPricing;
import org.example.domain.model.*;
import org.example.domain.money.Money;
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

import java.math.BigDecimal;
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

    private record PositionView(Instrument instrument, String secId, BigDecimal quantity,
                                BigDecimal openCost, BigDecimal marketValue,
                                BigDecimal unrealizedPnl) {
    }

    /**
     * Прогноз дохода на 12 месяцев в разрезе, которого требует налог: купоны и дивиденды
     * облагаются по разным правилам. Раньше это был {@code double[2]}, где смысл элемента
     * держался на комментарии у места вызова, — с переходом на {@link BigDecimal} массив
     * пришлось бы делать объектным, и именованные поля оказались и короче, и надёжнее.
     */
    private record ProjectedIncome(BigDecimal coupon, BigDecimal dividend) {

        private BigDecimal total() {
            return coupon.add(dividend);
        }
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
                            List<RealizedSale> realizedSales, BigDecimal totalCommission) {
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
        ProjectedIncome annual = projectedAnnualIncome(forecast);
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

            BigDecimal accountInvested = Money.sum(s.positions(), PositionView::openCost);
            BigDecimal accountValue = Money.sum(s.positions(), PositionView::marketValue);

            List<IncomeEvent> forecast = rawIncomeEvents(s);
            ProjectedIncome annual = projectedAnnualIncome(forecast);
            List<IncomeEvent> realizedIncome = realizedIncomeEvents(s.transactions(), s.instruments());

            totals.invested = totals.invested.add(accountInvested);
            totals.marketValue = totals.marketValue.add(accountValue);
            totals.realizedPnl = totals.realizedPnl.add(Money.sum(s.realizedSales(), RealizedSale::pnl));
            totals.commission = totals.commission.add(s.totalCommission());
            totals.receivedIncome = totals.receivedIncome.add(Money.sum(realizedIncome, IncomeEvent::gross));
            totals.projectedIncome = totals.projectedIncome.add(annual.total());
            totals.tax = totals.tax.add(computeTax(account, s.realizedSales(), annual).totalTax());
            totals.positions += s.positions().size();

            BigDecimal accountCash = freeCash(s.cashMovements(), s.pricedTrades(), realizedIncome,
                    depositsFundedFrom(deposits, account.getId()));
            totals.cashBalance = totals.cashBalance.add(accountCash);
            totals.addExternal(externalIn(s.cashMovements()), !s.transactions().isEmpty());

            lines.add(toAccountLine(account, s, accountInvested, accountValue, accountCash));
            // В разрезе по счетам показываем весь капитал счёта, включая непристроенные деньги
            BigDecimal accountCapital = accountValue.add(Money.atLeastZero(accountCash));
            if (Money.isPositive(accountCapital)) {
                accountHoldings.add(new Holding(account.getName(), accountCapital));
            }
            for (PositionView p : s.positions()) {
                typeHoldings.add(new Holding(byTypeDimension.categoryOf(p.instrument()), p.marketValue()));
            }
            forecast.forEach(e -> upcoming.add(toIncomeDto(e, account, couponTaxable, s.instruments())));
            flows.addAll(capitalFlows(s.transactions(), s.instruments(), s.cashMovements()));
        }

        LocalDate today = LocalDate.now();
        BigDecimal depositsValue = Money.ZERO;
        for (Deposit deposit : deposits) {
            BigDecimal value = depositCalculator.valueOn(deposit, today);
            depositsValue = depositsValue.add(value);
            if (Money.isPositive(value)) {
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

        if (Money.isPositive(totals.cashBalance)) {
            typeHoldings.add(new Holding("Свободные деньги", totals.cashBalance));
        }
        if (Money.isPositive(depositsValue)) {
            typeHoldings.add(new Holding("Вклады", depositsValue));
        }

        BigDecimal grandTotal = totals.totalValue().add(depositsValue);
        if (Money.isPositive(grandTotal)) {
            flows.add(new CashFlow(today, Money.toDouble(grandTotal)));
        }
        Double xirr = xirrCalculator.compute(flows);
        upcoming.sort(Comparator.comparing(IncomeEventDto::date));

        return new ConsolidatedOverviewDto(totals.toDto(grandTotal), lines,
                toSlices(accountHoldings), toSlices(typeHoldings), upcoming,
                xirr == null ? null : xirr * 100.0,
                depositService.list(clientId), Money.round(depositsValue), Money.round(grandTotal),
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
                        Money.round(a.amount()), Money.round(a.amount()), false, deposit.getName()))
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
            flows.add(new CashFlow(deposit.getOpenedOn(),
                    -Money.toDouble(deposit.getPrincipal())));
        }
        if (!deposit.isCapitalized()) {
            depositCalculator.payouts(deposit).stream()
                    .filter(a -> !a.date().isAfter(today))
                    .forEach(a -> flows.add(new CashFlow(a.date(), Money.toDouble(a.amount()))));
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
        BigDecimal interest = deposits.stream()
                .map(d -> depositCalculator.interestReceivedBetween(d, yearStart, yearEnd))
                .reduce(Money.ZERO, BigDecimal::add);
        // Ставка ЦБ приходит из внешнего источника числом с двумя знаками (14.25) — на границе
        // с денежным расчётом переводим её в точное десятичное представление
        BigDecimal keyRate = Money.of(keyRateService.maxForYear(today.getYear()));
        return depositTaxCalculator.compute(interest, keyRate);
    }

    /** Накопитель сумм по счетам — иначе метод свёлся бы к десятку скачущих локальных переменных. */
    private static final class Totals {
        private BigDecimal invested = Money.ZERO;
        private BigDecimal marketValue = Money.ZERO;
        private BigDecimal cashBalance = Money.ZERO;
        private BigDecimal realizedPnl = Money.ZERO;
        private BigDecimal commission = Money.ZERO;
        private BigDecimal receivedIncome = Money.ZERO;
        private BigDecimal projectedIncome = Money.ZERO;
        private BigDecimal tax = Money.ZERO;
        private int positions;
        private BigDecimal externalIn = Money.ZERO;
        /** Хоть один счёт с бумагами, но без учёта денег — и общая сумма внесённого уже не сумма. */
        private boolean externalComplete = true;

        /**
         * @param amount   внесено по этому источнику; {@code null} — учёт не ведётся
         * @param hasMoney есть ли по источнику вообще что считать: пустой счёт без сделок
         *                 и без движений итог не портит
         */
        private void addExternal(BigDecimal amount, boolean hasMoney) {
            if (amount != null) {
                externalIn = externalIn.add(amount);
            } else if (hasMoney) {
                externalComplete = false;
            }
        }

        private BigDecimal totalValue() {
            return marketValue.add(cashBalance);
        }

        /** @param grandTotal весь капитал клиента вместе с вкладами — прибыль считается от него */
        private PortfolioSummaryDto toDto(BigDecimal grandTotal) {
            BigDecimal external = externalComplete ? externalIn : null;
            BigDecimal profit = external == null ? null : grandTotal.subtract(external);
            return new PortfolioSummaryDto(
                    Money.round(invested), Money.round(marketValue), Money.round(cashBalance),
                    Money.round(totalValue()),
                    external == null ? null : Money.round(external),
                    profit == null ? null : Money.round(profit),
                    Money.round(marketValue.subtract(invested)), Money.round(realizedPnl),
                    Money.round(commission), Money.round(receivedIncome),
                    Money.round(projectedIncome), Money.round(tax), positions);
        }
    }

    private AccountLineDto toAccountLine(Account account, Snapshot s, BigDecimal invested,
                                         BigDecimal marketValue, BigDecimal cash) {
        BigDecimal pnl = marketValue.subtract(invested);
        return new AccountLineDto(account.getId(), account.getName(), account.getType(),
                account.getIisType(), Money.round(invested), Money.round(marketValue),
                Money.round(cash), Money.round(marketValue.add(cash)), Money.round(pnl),
                Money.percentOf(pnl, invested), s.positions().size());
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
        BigDecimal cash = freeCash(movements, pricedTrades(txns, instruments),
                realizedIncomeEvents(txns, instruments), fundedDeposits(account));
        double terminal = positionsValue + Money.toDouble(cash);
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
        // Граница с аналитикой: дневной ряд стоимости строится по свечам, то есть в double
        return txns.stream()
                .map(t -> new DatedTrade(t.getSecId(), t.getSide(), t.getTradeDate(),
                        Money.toDouble(t.getQuantity()),
                        Money.toDouble(InstrumentPricing.tradeMoneyPerUnit(
                                instruments.get(t.getSecId()), t.getPrice(), t.getAci())),
                        Money.toDouble(t.getCommission())))
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
                closes.put(date, Money.toDouble(
                        InstrumentPricing.moneyPerUnit(instrument, Money.of(candle.close()))));
            }
        } catch (RuntimeException e) {
            log.warn("История цен по {} недоступна: {}", secId, e.getMessage());
        }
        if (closes.isEmpty()) {
            double lastKnown = Money.toDouble(InstrumentPricing.currentMoneyPerUnit(instrument));
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
                .map(m -> new CashFlow(m.getMovedOn(), -Money.toDouble(m.signedAmount())))
                .toList();
    }

    /**
     * Свободные деньги счёта — или 0, если учёт денег по счёту не ведётся
     * (см. {@link CashBalanceCalculator#reportedBalance}).
     *
     * @param fundedDeposits тело вкладов, открытых деньгами этого счёта: они со счёта ушли
     *                       и теперь лежат во вкладе, где их и показывает сводка
     */
    private BigDecimal freeCash(List<CashMovement> movements, List<PricedTrade> trades,
                                List<IncomeEvent> receivedIncome, BigDecimal fundedDeposits) {
        BigDecimal externalNet = Money.sum(movements, CashMovement::signedAmount);
        BigDecimal received = Money.sum(receivedIncome, IncomeEvent::gross);
        BigDecimal balance = cashBalanceCalculator
                .reportedBalance(!movements.isEmpty(), externalNet, trades, received);
        return movements.isEmpty() ? balance : balance.subtract(fundedDeposits);
    }

    /**
     * Тело вкладов, открытых деньгами этого счёта. Отдельный запрос — вклады привязаны к клиенту,
     * а не к счёту, и в снапшот счёта они не попадают.
     */
    private BigDecimal fundedDeposits(Account account) {
        return depositsFundedFrom(depositService.entities(account.getClientId()), account.getId());
    }

    /** Тело вкладов, открытых деньгами конкретного счёта. */
    private static BigDecimal depositsFundedFrom(List<Deposit> deposits, Long accountId) {
        return deposits.stream()
                .filter(d -> accountId.equals(d.getSourceAccountId()))
                .map(Deposit::getPrincipal)
                .reduce(Money.ZERO, BigDecimal::add);
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
            BigDecimal money = InstrumentPricing
                    .tradeMoneyPerUnit(instruments.get(t.getSecId()), t.getPrice(), t.getAci())
                    .multiply(t.getQuantity());
            BigDecimal signed = t.getSide() == TradeSide.BUY
                    ? money.add(t.getCommission()).negate()
                    : money.subtract(t.getCommission());
            flows.add(new CashFlow(t.getTradeDate(), Money.toDouble(signed)));
        }
        realizedIncomeEvents(txns, instruments)
                .forEach(e -> flows.add(new CashFlow(e.date(), Money.toDouble(e.gross()))));
        return flows;
    }

    public PortfolioSummaryDto summary(String clientId, Long accountId) {
        Account account = accountService.getOwned(clientId, accountId);
        Snapshot s = snapshot(account);

        // Прогноз считается один раз и переиспользуется в налоге — обход выплат ходит во внешний источник
        ProjectedIncome annual = projectedAnnualIncome(rawIncomeEvents(s));
        BigDecimal estimatedTax = computeTax(account, s.realizedSales(), annual).totalTax();
        return summaryOf(s, annual, estimatedTax,
                realizedIncomeEvents(s.transactions(), s.instruments()));
    }

    private PortfolioSummaryDto summaryOf(Snapshot s, ProjectedIncome annual,
                                          BigDecimal estimatedTax,
                                          List<IncomeEvent> realizedIncome) {
        BigDecimal invested = Money.sum(s.positions(), PositionView::openCost);
        BigDecimal marketValue = Money.sum(s.positions(), PositionView::marketValue);
        BigDecimal realized = Money.sum(s.realizedSales(), RealizedSale::pnl);
        BigDecimal receivedIncome = Money.sum(realizedIncome, IncomeEvent::gross);
        BigDecimal cash = freeCash(s.cashMovements(), s.pricedTrades(), realizedIncome,
                fundedDeposits(s.account()));
        BigDecimal totalValue = marketValue.add(cash);

        BigDecimal externalIn = externalIn(s.cashMovements());
        BigDecimal profit = profitOver(totalValue, externalIn);
        return new PortfolioSummaryDto(
                Money.round(invested), Money.round(marketValue), Money.round(cash),
                Money.round(totalValue),
                externalIn == null ? null : Money.round(externalIn),
                profit == null ? null : Money.round(profit),
                Money.round(marketValue.subtract(invested)), Money.round(realized),
                Money.round(s.totalCommission()), Money.round(receivedIncome),
                Money.round(annual.total()), Money.round(estimatedTax),
                s.positions().size());
    }

    /**
     * Внесено извне: пополнения минус выводы. {@code null}, если движений нет вовсе — тогда
     * неизвестно не «ноль», а именно «неизвестно», и прибыль от такой базы считать нельзя.
     */
    private static BigDecimal externalIn(List<CashMovement> movements) {
        return movements.isEmpty() ? null : Money.sum(movements, CashMovement::signedAmount);
    }

    /**
     * Заработано за всё время = капитал сейчас − внесено извне.
     * <p>
     * Единственный способ сложить рост цены, полученные выплаты и результат продаж, не считая
     * ничего дважды: реинвестированный купон уже сидит и в себестоимости позиций, и в их
     * рыночной стоимости, поэтому «вложено + получено» капиталом не является.
     */
    private static BigDecimal profitOver(BigDecimal totalValue, BigDecimal externalIn) {
        return externalIn == null ? null : totalValue.subtract(externalIn);
    }

    // --- Ядро расчёта ---

    private Snapshot snapshot(Account account) {
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(account.getId());
        List<CashMovement> movements = cashMovementRepository
                .findByAccountIdOrderByMovedOnAscIdAsc(account.getId());
        BigDecimal totalCommission = Money.sum(txns, Transaction::getCommission);

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

            BigDecimal qty = fifo.openQuantity();
            if (Money.isPositive(qty)) {
                BigDecimal openCost = fifo.openCost();
                // Грязная цена: себестоимость лота уже содержит уплаченный НКД
                BigDecimal marketValue = InstrumentPricing
                        .currentPositionMoneyPerUnit(inst).multiply(qty);
                positions.add(new PositionView(inst, entry.getKey(), qty, openCost,
                        marketValue, marketValue.subtract(openCost)));
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
        BigDecimal qty = quantityAsOf(txns, date);
        if (Money.isPositive(qty)) {
            events.add(new IncomeEvent(date, secId, kind, Money.of(perUnit).multiply(qty), false));
        }
    }

    /** Количество бумаг на конец дня {@code date} по истории сделок (BUY − SELL, tradeDate ≤ date). */
    private BigDecimal quantityAsOf(List<Transaction> txns, LocalDate date) {
        BigDecimal qty = Money.ZERO;
        for (Transaction t : txns) {
            if (!t.getTradeDate().isAfter(date)) {
                qty = t.getSide() == TradeSide.BUY
                        ? qty.add(t.getQuantity())
                        : qty.subtract(t.getQuantity());
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

    /** Купоны и дивиденды за ближайшие 12 месяцев (амортизации не входят в доход). */
    private ProjectedIncome projectedAnnualIncome(List<IncomeEvent> events) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusMonths(PROJECTION_MONTHS);
        BigDecimal coupon = Money.ZERO;
        BigDecimal dividend = Money.ZERO;
        for (IncomeEvent e : events) {
            if (e.date().isBefore(from) || e.date().isAfter(to)) {
                continue;
            }
            if (e.kind() == IncomeKind.COUPON) {
                coupon = coupon.add(e.gross());
            } else if (e.kind() == IncomeKind.DIVIDEND) {
                dividend = dividend.add(e.gross());
            }
        }
        return new ProjectedIncome(coupon, dividend);
    }

    /** @param annual считается вызывающим, чтобы не обходить выплаты дважды. */
    private TaxBreakdown computeTax(Account account, List<RealizedSale> sales,
                                    ProjectedIncome annual) {
        TaxContext ctx = new TaxContext(account, sales, annual.coupon(), annual.dividend());
        return taxStrategyFactory.forAccount(account).compute(ctx);
    }

    // --- Мапперы ---

    private PositionDto toDto(PositionView p) {
        BigDecimal avgCost = Money.isPositive(p.quantity())
                ? Money.roundUnit(Money.divide(p.openCost(), p.quantity()))
                : Money.ZERO;
        BigDecimal currentPrice = p.instrument() == null
                ? Money.ZERO
                : Money.of(p.instrument().getLastPrice());
        InstrumentType type = p.instrument() != null ? p.instrument().getType() : null;
        String name = p.instrument() != null ? p.instrument().getShortName() : p.secId();
        return new PositionDto(p.secId(), type, name, p.quantity(), avgCost,
                Money.round(p.openCost()), currentPrice, Money.round(p.marketValue()),
                Money.round(p.unrealizedPnl()), Money.percentOf(p.unrealizedPnl(), p.openCost()));
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
        BigDecimal net = switch (e.kind()) {
            case AMORTIZATION -> e.gross();  // возврат тела долга, не доход
            // Проценты вкладов сюда не попадают (у них свой путь), но ветка обязана существовать:
            // налог по ним считается от годовой суммы всех вкладов, а не с отдельной выплаты
            case INTEREST -> e.gross();
            case COUPON -> couponTaxable ? afterTax(e.gross(), account.getBaseTaxRate()) : e.gross();
            case DIVIDEND -> afterTax(e.gross(), TaxRates.DIVIDEND_PCT);
        };
        return new IncomeEventDto(e.date(), e.secId(), nameOf(instruments, e.secId()), e.kind(),
                Money.round(e.gross()), Money.round(net), e.estimate(), account.getName());
    }

    /** Выплата «на руки»: сумма минус НДФЛ по ставке. */
    private static BigDecimal afterTax(BigDecimal gross, BigDecimal ratePct) {
        return gross.subtract(Money.pct(gross, ratePct));
    }

    private boolean isCouponTaxable(Account account) {
        return !(account.getType() == AccountType.IIS && account.getIisType() == IisType.B);
    }
}
