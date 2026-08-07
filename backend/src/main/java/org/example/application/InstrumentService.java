package org.example.application;

import org.example.application.portfolio.InstrumentPricing;
import org.example.domain.money.Money;
import org.example.domain.analysis.BondAnalyzer;
import org.example.domain.analysis.DividendSummary;
import org.example.domain.analysis.DividendYieldCalculator;
import org.example.domain.analysis.PerformanceMetrics;
import org.example.domain.analysis.PricePoint;
import org.example.domain.analysis.ReturnsCalculator;
import org.example.domain.filter.BondSpecifications;
import org.example.domain.filter.FilterCriteria;
import org.example.domain.filter.ShareSpecifications;
import org.example.domain.model.AccruedInterest;
import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.model.InstrumentType;
import org.example.domain.model.DividendPayment;
import org.example.domain.model.Share;
import org.example.domain.port.BondHistoryProvider;
import org.example.domain.port.CorporateActionsProvider;
import org.example.infrastructure.persistence.BondRepository;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.ShareRepository;
import org.example.web.dto.AccruedInterestDto;
import org.example.web.dto.DividendInfoDto;
import org.example.web.dto.InstrumentDto;
import org.example.web.dto.InstrumentPaymentDto;
import org.example.web.dto.PagedResponse;
import org.example.web.dto.PerformanceDto;
import org.example.web.dto.SummaryDto;
import org.example.web.mapper.InstrumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Фасад чтения инструментов: диспетчеризация по типу (Strategy), фильтрация через
 * Specification, кеширование в Redis и агрегированная сводка.
 */
@Service
@Transactional(readOnly = true)
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);
    /** Код дневного интервала свечей MOEX. */
    private static final int DAILY_INTERVAL = 24;

    private final BondRepository bondRepository;
    private final ShareRepository shareRepository;
    private final InstrumentRepository instrumentRepository;
    private final BondAnalyzer bondAnalyzer;
    private final CorporateActionsProvider corporateActions;
    private final BondHistoryProvider bondHistory;
    private final CandleService candleService;
    private final ReturnsCalculator returnsCalculator;
    private final DividendYieldCalculator dividendYieldCalculator;

    public InstrumentService(BondRepository bondRepository,
                             ShareRepository shareRepository,
                             InstrumentRepository instrumentRepository,
                             BondAnalyzer bondAnalyzer,
                             CorporateActionsProvider corporateActions,
                             BondHistoryProvider bondHistory,
                             CandleService candleService,
                             ReturnsCalculator returnsCalculator,
                             DividendYieldCalculator dividendYieldCalculator) {
        this.bondRepository = bondRepository;
        this.shareRepository = shareRepository;
        this.instrumentRepository = instrumentRepository;
        this.bondAnalyzer = bondAnalyzer;
        this.corporateActions = corporateActions;
        this.bondHistory = bondHistory;
        this.candleService = candleService;
        this.returnsCalculator = returnsCalculator;
        this.dividendYieldCalculator = dividendYieldCalculator;
    }

    /**
     * НКД на одну облигацию на дату сделки — то, что подставляется в форму ввода.
     * <p>
     * За сегодня берётся значение из справочника: оно уже загружено синхронизацией, и ходить
     * за ним в историю торгов незачем — там сегодняшней сессии может ещё не быть. За прошедшую
     * дату спрашиваем историю: рассчитать НКД самим нельзя, у флоатеров ставка будущих периодов
     * не зафиксирована, а биржа НКД считает по каждому торговому дню.
     * <p>
     * Недоступность MOEX не должна ломать ввод сделки: возвращаем «неизвестно» и даём вписать руками.
     */
    public AccruedInterestDto accruedInterest(String secId, LocalDate date) {
        Instrument inst = instrumentRepository.findBySecId(secId)
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + secId));
        if (!(inst instanceof Bond bond)) {
            return AccruedInterestDto.notABond(secId, date);
        }
        LocalDate today = LocalDate.now();
        if (!date.isBefore(today)) {
            return new AccruedInterestDto(secId, date, true, bond.getAccruedInterest(), today);
        }
        try {
            AccruedInterest history = bondHistory.accruedInterestOn(secId, date);
            return history.known()
                    ? new AccruedInterestDto(secId, date, true, history.perBond(), history.tradeDate())
                    : AccruedInterestDto.unknown(secId, date);
        } catch (RuntimeException e) {
            log.warn("НКД по {} на {} недоступен: {}", secId, date, e.getMessage());
            return AccruedInterestDto.unknown(secId, date);
        }
    }

    /** История выплат инструмента из MOEX: купоны/амортизации (облигация) или дивиденды (акция), новые сверху. */
    @Cacheable(cacheNames = "payments", key = "#secId")
    @Transactional
    public List<InstrumentPaymentDto> payments(String secId) {
        Instrument inst = instrumentRepository.findBySecId(secId)
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + secId));

        List<Payout> payouts = new ArrayList<>();
        try {
            if (inst instanceof Bond) {
                corporateActions.coupons(secId).forEach(c ->
                        payouts.add(new Payout(c.date(), "COUPON", c.perBond(), "RUB", c.estimated())));
                corporateActions.amortizations(secId).forEach(a ->
                        payouts.add(new Payout(a.date(), "AMORTIZATION", a.perBond(), "RUB", false)));
            } else if (inst instanceof Share) {
                corporateActions.dividends(secId).forEach(d ->
                        payouts.add(new Payout(d.recordDate(), "DIVIDEND", d.perShare(), d.currency(), false)));
            }
        } catch (RuntimeException e) {
            log.warn("История выплат по {} недоступна: {}", secId, e.getMessage());
        }

        NavigableMap<LocalDate, Double> historicalPrices = historicalPrices(secId, inst, payouts);
        double currentPrice = Money.toDouble(InstrumentPricing.currentMoneyPerUnit(inst));

        return payouts.stream()
                .map(p -> new InstrumentPaymentDto(p.date(), p.kind(), p.perUnit(), p.currency(),
                        sharePct(p, historicalPrices, currentPrice), p.estimated()))
                .sorted(Comparator.comparing(InstrumentPaymentDto::date).reversed())
                .toList();
    }

    private record Payout(LocalDate date, String kind, double perUnit, String currency,
                          boolean estimated) {
    }

    /**
     * Доля выплаты в цене бумаги на <b>её собственную дату</b>: дивиденд, выплаченный когда акция
     * стоила 100 ₽, был доходностью 10%, и пересчёт по сегодняшней цене превратил бы его в 4%,
     * то есть в число, которого никогда не существовало.
     * <p>
     * Будущие выплаты считаются от текущей цены — другой у них быть не может. К ней же откатываемся,
     * если свечи на дату отсечки нет (бумага тогда не торговалась или история не доехала).
     */
    private Double sharePct(Payout payout, NavigableMap<LocalDate, Double> prices, double currentPrice) {
        Map.Entry<LocalDate, Double> onDate = prices.floorEntry(payout.date());
        double price = onDate != null ? onDate.getValue() : currentPrice;
        return price > 0 ? payout.perUnit() / price * 100.0 : null;
    }

    /**
     * Дневные цены закрытия за период выплат, приведённые к деньгам за бумагу: котировка облигации
     * приходит в процентах от номинала, и делить рублёвый купон на «98.4» дало бы доходность,
     * завышенную в десятки раз.
     */
    private NavigableMap<LocalDate, Double> historicalPrices(String secId, Instrument inst,
                                                             List<Payout> payouts) {
        LocalDate today = LocalDate.now();
        Optional<LocalDate> earliest = payouts.stream()
                .map(Payout::date)
                .filter(d -> !d.isAfter(today))
                .min(Comparator.naturalOrder());
        if (earliest.isEmpty()) {
            return Collections.emptyNavigableMap();
        }
        try {
            TreeMap<LocalDate, Double> prices = new TreeMap<>();
            for (var candle : candleService.getCandles(secId, DAILY_INTERVAL, earliest.get(), today)) {
                prices.put(Instant.ofEpochSecond(candle.time()).atZone(ZoneOffset.UTC).toLocalDate(),
                        Money.toDouble(InstrumentPricing.moneyPerUnit(inst, Money.of(candle.close()))));
            }
            return prices;
        } catch (RuntimeException e) {
            log.warn("Котировки для долей выплат по {} недоступны, берём текущую цену: {}",
                    secId, e.getMessage());
            return Collections.emptyNavigableMap();
        }
    }

    /**
     * Дивидендная доходность акции за скользящие 12 месяцев (расчёт — {@link DividendYieldCalculator}).
     * Для облигаций и при отсутствии данных — пустая сводка.
     */
    @Cacheable(cacheNames = "dividendYield", key = "#secId")
    public DividendInfoDto dividendYield(String secId) {
        Instrument inst = instrumentRepository.findBySecId(secId)
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + secId));
        if (!(inst instanceof Share share)) {
            return new DividendInfoDto(null, null, 0);
        }
        double price = share.getLastPrice() == null ? 0.0 : share.getLastPrice();
        List<DividendPayment> dividends;
        try {
            dividends = corporateActions.dividends(secId);
        } catch (RuntimeException e) {
            log.warn("Дивиденды по {} недоступны: {}", secId, e.getMessage());
            return new DividendInfoDto(null, null, 0);
        }
        DividendSummary s = dividendYieldCalculator.trailing(dividends, price, LocalDate.now());
        return new DividendInfoDto(s.trailingSum(), s.yieldPct(), s.count());
    }

    /**
     * Доходности инструмента за периоды (1Д/1Н/1М/1Г/YTD) и 52-недельный диапазон,
     * посчитанные из дневных свечей за последние ~13 месяцев. Расчёт — в {@link ReturnsCalculator}.
     */
    @Cacheable(cacheNames = "performance", key = "#secId")
    @Transactional
    public PerformanceDto performance(String secId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(1).minusMonths(1);
        List<PricePoint> points = candleService.getCandles(secId, DAILY_INTERVAL, from, to).stream()
                .map(c -> new PricePoint(
                        Instant.ofEpochSecond(c.time()).atZone(ZoneOffset.UTC).toLocalDate(),
                        c.high(), c.low(), c.close()))
                .toList();
        PerformanceMetrics m = returnsCalculator.compute(points, to);
        return new PerformanceDto(m.lastPrice(), m.change1d(), m.change1w(), m.change1m(),
                m.change1y(), m.changeYtd(), m.high52w(), m.low52w());
    }

    @Cacheable(cacheNames = "instruments",
            key = "#criteria.toString() + '|' + #pageable.toString()")
    public PagedResponse<InstrumentDto> query(FilterCriteria criteria, Pageable pageable) {
        InstrumentType type = criteria.getType();
        Pageable effective = withDefaultSort(pageable, type);

        Page<? extends Instrument> page = switch (type == null ? InstrumentType.BOND : type) {
            case BOND -> bondRepository.findAll(BondSpecifications.fromCriteria(criteria), effective);
            case SHARE -> shareRepository.findAll(ShareSpecifications.fromCriteria(criteria), effective);
        };

        Page<InstrumentDto> dtoPage = page.map(InstrumentMapper::toDto);
        SummaryDto summary = (type == InstrumentType.BOND) ? bondSummary(criteria) : null;
        return PagedResponse.of(dtoPage, summary);
    }

    /** Смешанный поиск по всем инструментам (общие поля) — для глобального поиска. */
    @Cacheable(cacheNames = "instruments",
            key = "'ALL|' + #criteria.toString() + '|' + #pageable.toString()")
    public PagedResponse<InstrumentDto> queryAll(FilterCriteria criteria, Pageable pageable) {
        String q = criteria.getQuery() == null ? "" : criteria.getQuery().trim().toLowerCase();
        if (q.isEmpty()) {
            return PagedResponse.of(Page.<InstrumentDto>empty(pageable), null);
        }
        Page<InstrumentDto> dtoPage = instrumentRepository
                .search(q, "%" + q + "%", q + "%", pageable)
                .map(InstrumentMapper::toDto);
        return PagedResponse.of(dtoPage, null);
    }

    /** Детальная карточка инструмента по коду бумаги. */
    @Cacheable(cacheNames = "instrument", key = "#secId")
    public InstrumentDto getBySecId(String secId) {
        return instrumentRepository.findBySecId(secId)
                .map(InstrumentMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + secId));
    }

    /** Инструменты для сравнения бок-о-бок, в порядке переданных кодов. */
    public List<InstrumentDto> compare(List<String> secIds) {
        Map<String, Instrument> bySecId = instrumentRepository.findAllBySecIdIn(secIds).stream()
                .collect(java.util.stream.Collectors.toMap(Instrument::getSecId, i -> i));
        return secIds.stream()
                .map(bySecId::get)
                .filter(java.util.Objects::nonNull)
                .map(InstrumentMapper::toDto)
                .toList();
    }

    /** Все инструменты, удовлетворяющие критериям (без пагинации) — для экспорта. */
    public List<InstrumentDto> findAllForExport(FilterCriteria criteria) {
        InstrumentType type = criteria.getType() == null ? InstrumentType.BOND : criteria.getType();
        return switch (type) {
            case BOND -> bondRepository.findAll(BondSpecifications.fromCriteria(criteria)).stream()
                    .map(InstrumentMapper::toDto).toList();
            case SHARE -> shareRepository.findAll(ShareSpecifications.fromCriteria(criteria)).stream()
                    .map(InstrumentMapper::toDto).toList();
        };
    }

    @Cacheable(cacheNames = "summary", key = "#criteria.toString()")
    public SummaryDto bondSummary(FilterCriteria criteria) {
        List<Bond> matched = bondRepository.findAll(BondSpecifications.fromCriteria(criteria));
        return new SummaryDto(
                matched.size(),
                bondAnalyzer.calculateAverageYield(matched),
                bondAnalyzer.calculateAverageReliability(matched),
                bondAnalyzer.investmentGradeShare(matched),
                bondAnalyzer.maxYield(matched)
        );
    }

    /** Если клиент не задал сортировку — применяем разумную по умолчанию для типа. */
    private Pageable withDefaultSort(Pageable pageable, InstrumentType type) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort defaultSort = (type == InstrumentType.SHARE)
                ? Sort.by(Sort.Direction.DESC, "tradeVolume")
                : Sort.by(Sort.Direction.DESC, "reliabilityScore")
                    .and(Sort.by(Sort.Direction.DESC, "yieldToMaturity"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }
}
