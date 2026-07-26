package org.example.application;

import org.example.application.portfolio.InstrumentPricing;
import org.example.domain.model.*;
import org.example.domain.portfolio.*;
import org.example.domain.portfolio.allocation.AllocationCalculator;
import org.example.domain.portfolio.allocation.AllocationDimension;
import org.example.domain.portfolio.allocation.AllocationDimensionFactory;
import org.example.domain.portfolio.allocation.Holding;
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
import org.example.web.dto.portfolio.AllocationSliceDto;
import org.example.web.dto.portfolio.IncomeEventDto;
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
    /** Код дневного интервала свечей MOEX. */
    private static final int DAILY_INTERVAL = 24;

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
                            XirrCalculator xirrCalculator) {
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
     * @param instruments бумаги всех сделок счёта (не только открытых позиций) — держим в снапшоте,
     *                    чтобы расчёт полученных выплат не перечитывал их из БД второй раз.
     */
    private record Snapshot(Account account, List<Transaction> transactions,
                            Map<String, Instrument> instruments,
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
                .map(e -> toIncomeDto(e, account, couponTaxable))
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
        return toReceivedDtos(realizedIncomeEvents(txns, loadInstruments(txns)), account);
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
                forecast.stream().map(e -> toIncomeDto(e, account, couponTaxable)).toList(),
                toReceivedDtos(realized, account),
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
        return allocationCalculator.breakdown(holdings).stream()
                .map(sl -> new AllocationSliceDto(sl.category(), sl.value(), sl.percent()))
                .toList();
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
        Double xirr = computeXirr(txns, instruments, series, to);

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

    /** Дневные цены закрытия, приведённые к деньгам за бумагу. Сбой источника → бумага без истории. */
    private Map<LocalDate, Double> dailyCloses(String secId, Instrument instrument,
                                               LocalDate from, LocalDate to) {
        try {
            Map<LocalDate, Double> closes = new HashMap<>();
            for (var candle : candleService.getCandles(secId, DAILY_INTERVAL, from, to)) {
                LocalDate date = Instant.ofEpochSecond(candle.time())
                        .atZone(ZoneOffset.UTC).toLocalDate();
                closes.put(date, InstrumentPricing.moneyPerUnit(instrument, candle.close()));
            }
            return closes;
        } catch (RuntimeException e) {
            log.warn("История цен по {} недоступна, бумага не войдёт в ряд стоимости: {}", secId, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Потоки для XIRR: покупки — со знаком минус (с комиссией), продажи — плюс (за вычетом комиссии),
     * полученные купоны и дивиденды — плюс, и завершающим потоком текущая стоимость позиций.
     */
    private Double computeXirr(List<Transaction> txns, Map<String, Instrument> instruments,
                               List<ValuePoint> series, LocalDate to) {
        List<CashFlow> flows = new ArrayList<>();
        for (Transaction t : txns) {
            double money = t.getQuantity() * InstrumentPricing.tradeMoneyPerUnit(
                    instruments.get(t.getSecId()), t.getPrice(), t.getAci());
            flows.add(new CashFlow(t.getTradeDate(), t.getSide() == TradeSide.BUY
                    ? -(money + t.getCommission())
                    : money - t.getCommission()));
        }
        realizedIncomeEvents(txns, instruments).forEach(e -> flows.add(new CashFlow(e.date(), e.gross())));

        double terminalValue = series.isEmpty() ? 0.0 : series.get(series.size() - 1).value();
        if (terminalValue > 0) {
            flows.add(new CashFlow(to, terminalValue));
        }
        return xirrCalculator.compute(flows);
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

        return new PortfolioSummaryDto(invested, marketValue, marketValue - invested, realized,
                s.totalCommission(), receivedIncome, annual[0] + annual[1], estimatedTax,
                s.positions().size());
    }

    // --- Ядро расчёта ---

    private Snapshot snapshot(Account account) {
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(account.getId());
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
                double marketValue = InstrumentPricing.currentMoneyPerUnit(inst) * qty;
                positions.add(new PositionView(inst, entry.getKey(), qty, openCost,
                        marketValue, marketValue - openCost));
            }
        }
        return new Snapshot(account, txns, instruments, positions, realized, totalCommission);
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
    private List<IncomeEventDto> toReceivedDtos(List<IncomeEvent> events, Account account) {
        boolean couponTaxable = isCouponTaxable(account);
        return events.stream()
                .sorted(Comparator.comparing(IncomeEvent::date).reversed())
                .map(e -> toIncomeDto(e, account, couponTaxable))
                .toList();
    }

    private IncomeEventDto toIncomeDto(IncomeEvent e, Account account, boolean couponTaxable) {
        double net = switch (e.kind()) {
            case AMORTIZATION -> e.gross();  // возврат тела долга, не доход
            case COUPON -> couponTaxable ? e.gross() * (1 - account.getBaseTaxRate() / 100.0) : e.gross();
            case DIVIDEND -> e.gross() * (1 - TaxRates.DIVIDEND_PCT / 100.0);
        };
        return new IncomeEventDto(e.date(), e.secId(), e.kind(), e.gross(), net, e.estimate());
    }

    private boolean isCouponTaxable(Account account) {
        return !(account.getType() == AccountType.IIS && account.getIisType() == IisType.B);
    }
}
