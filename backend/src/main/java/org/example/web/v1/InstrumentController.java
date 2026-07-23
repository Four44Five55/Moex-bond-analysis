package org.example.web.v1;

import org.example.application.CandleService;
import org.example.application.InstrumentService;
import org.example.application.SyncService;
import org.example.domain.filter.FilterCriteria;
import org.example.domain.model.InstrumentType;
import org.example.web.dto.CandleDto;
import org.example.web.dto.InstrumentDto;
import org.example.web.dto.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final SyncService syncService;
    private final CandleService candleService;

    public InstrumentController(InstrumentService instrumentService,
                                SyncService syncService,
                                CandleService candleService) {
        this.instrumentService = instrumentService;
        this.syncService = syncService;
        this.candleService = candleService;
    }

    /** Список инструментов одного типа с фильтрами/сортировкой/пагинацией. */
    @GetMapping
    public PagedResponse<InstrumentDto> list(
            @RequestParam(defaultValue = "BOND") InstrumentType type,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") double minYield,
            @RequestParam(defaultValue = "100") double maxDuration,
            @RequestParam(defaultValue = "1") int minReliability,
            @RequestParam(defaultValue = "false") boolean onlyInvestmentGrade,
            @RequestParam(defaultValue = "true") boolean excludeSuspicious,
            @RequestParam(required = false) String sector,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        FilterCriteria criteria = FilterCriteria.builder()
                .type(type)
                .query(query)
                .minYield(minYield)
                .maxDuration(maxDuration)
                .minReliabilityScore(minReliability)
                .onlyInvestmentGrade(onlyInvestmentGrade)
                .excludeSuspicious(excludeSuspicious)
                .sector(sector)
                .build();
        return instrumentService.query(criteria, pageable);
    }

    /** Глобальный поиск по облигациям и акциям (общие поля). */
    @GetMapping("/search")
    public PagedResponse<InstrumentDto> search(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        FilterCriteria criteria = FilterCriteria.builder().query(query).build();
        return instrumentService.queryAll(criteria, pageable);
    }

    /** Сравнение инструментов бок-о-бок (secIds — список кодов через запятую). */
    @GetMapping("/compare")
    public List<InstrumentDto> compare(@RequestParam List<String> secIds) {
        return instrumentService.compare(secIds);
    }

    /** Детальная карточка инструмента. */
    @GetMapping("/{secId}")
    public InstrumentDto detail(@PathVariable String secId) {
        return instrumentService.getBySecId(secId);
    }

    /** Исторические свечи инструмента (по умолчанию дневные за последний год). */
    @GetMapping("/{secId}/candles")
    public List<CandleDto> candles(
            @PathVariable String secId,
            @RequestParam(defaultValue = "24") int interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate fromDate = from != null ? from : LocalDate.now().minusYears(1);
        LocalDate toDate = to != null ? to : LocalDate.now();
        return candleService.getCandles(secId, interval, fromDate, toDate);
    }

    /** Ручная синхронизация с MOEX (облигации + акции). */
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        int synced = syncService.syncAll();
        return Map.of("synced", synced);
    }
}
