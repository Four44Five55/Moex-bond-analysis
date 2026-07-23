package org.example.application;

import org.example.application.portfolio.InstrumentPricing;
import org.example.domain.model.*;
import org.example.domain.portfolio.*;
import org.example.domain.portfolio.tax.TaxBreakdown;
import org.example.domain.portfolio.tax.TaxContext;
import org.example.domain.portfolio.tax.TaxStrategyFactory;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.TransactionRepository;
import org.example.domain.port.CorporateActionsProvider;
import org.example.web.dto.portfolio.IncomeEventDto;
import org.example.web.dto.portfolio.PortfolioSummaryDto;
import org.example.web.dto.portfolio.PositionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final FifoCalculator fifoCalculator;
    private final IncomeForecaster incomeForecaster;
    private final CorporateActionsProvider corporateActions;
    private final TaxStrategyFactory taxStrategyFactory;

    public PortfolioService(AccountService accountService,
                            TransactionRepository transactionRepository,
                            InstrumentRepository instrumentRepository,
                            FifoCalculator fifoCalculator,
                            IncomeForecaster incomeForecaster,
                            CorporateActionsProvider corporateActions,
                            TaxStrategyFactory taxStrategyFactory) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.instrumentRepository = instrumentRepository;
        this.fifoCalculator = fifoCalculator;
        this.incomeForecaster = incomeForecaster;
        this.corporateActions = corporateActions;
        this.taxStrategyFactory = taxStrategyFactory;
    }

    private record PositionView(Instrument instrument, String secId, double quantity,
                                double openCost, double marketValue, double unrealizedPnl) {
    }

    private record Snapshot(Account account, List<PositionView> positions,
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
        return computeTax(account, snapshot(account));
    }

    public PortfolioSummaryDto summary(String clientId, Long accountId) {
        Account account = accountService.getOwned(clientId, accountId);
        Snapshot s = snapshot(account);

        double invested = s.positions().stream().mapToDouble(PositionView::openCost).sum();
        double marketValue = s.positions().stream().mapToDouble(PositionView::marketValue).sum();
        double unrealized = marketValue - invested;
        double realized = s.realizedSales().stream().mapToDouble(RealizedSale::pnl).sum();

        double[] annual = projectedAnnualIncome(rawIncomeEvents(s));
        double projectedIncome = annual[0] + annual[1];
        double estimatedTax = computeTax(account, s).totalTax();

        return new PortfolioSummaryDto(invested, marketValue, unrealized, realized,
                s.totalCommission(), projectedIncome, estimatedTax, s.positions().size());
    }

    // --- Ядро расчёта ---

    private Snapshot snapshot(Account account) {
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(account.getId());
        double totalCommission = txns.stream().mapToDouble(Transaction::getCommission).sum();

        Map<String, List<Transaction>> bySec = txns.stream()
                .collect(Collectors.groupingBy(Transaction::getSecId, LinkedHashMap::new, Collectors.toList()));
        Map<String, Instrument> instruments = instrumentRepository.findAllBySecIdIn(new ArrayList<>(bySec.keySet()))
                .stream().collect(Collectors.toMap(Instrument::getSecId, i -> i));

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
        return new Snapshot(account, positions, realized, totalCommission);
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
            if (e.date().isAfter(to)) {
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

    private TaxBreakdown computeTax(Account account, Snapshot s) {
        double[] annual = projectedAnnualIncome(rawIncomeEvents(s));
        TaxContext ctx = new TaxContext(account, s.realizedSales(), annual[0], annual[1]);
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

    private IncomeEventDto toIncomeDto(IncomeEvent e, Account account, boolean couponTaxable) {
        double net = switch (e.kind()) {
            case AMORTIZATION -> e.gross();
            case COUPON -> couponTaxable ? e.gross() * (1 - account.getBaseTaxRate() / 100.0) : e.gross();
            case DIVIDEND -> e.gross() * (1 - 0.13);
        };
        return new IncomeEventDto(e.date(), e.secId(), e.kind(), e.gross(), net, e.estimate());
    }

    private boolean isCouponTaxable(Account account) {
        return !(account.getType() == AccountType.IIS && account.getIisType() == IisType.B);
    }
}
