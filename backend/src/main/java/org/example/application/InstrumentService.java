package org.example.application;

import org.example.domain.analysis.BondAnalyzer;
import org.example.domain.filter.BondSpecifications;
import org.example.domain.filter.FilterCriteria;
import org.example.domain.filter.InstrumentSpecifications;
import org.example.domain.filter.ShareSpecifications;
import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.model.InstrumentType;
import org.example.infrastructure.persistence.BondRepository;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.ShareRepository;
import org.example.web.dto.InstrumentDto;
import org.example.web.dto.PagedResponse;
import org.example.web.dto.SummaryDto;
import org.example.web.mapper.InstrumentMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Фасад чтения инструментов: диспетчеризация по типу (Strategy), фильтрация через
 * Specification, кеширование в Redis и агрегированная сводка.
 */
@Service
@Transactional(readOnly = true)
public class InstrumentService {

    private final BondRepository bondRepository;
    private final ShareRepository shareRepository;
    private final InstrumentRepository instrumentRepository;
    private final BondAnalyzer bondAnalyzer;

    public InstrumentService(BondRepository bondRepository,
                             ShareRepository shareRepository,
                             InstrumentRepository instrumentRepository,
                             BondAnalyzer bondAnalyzer) {
        this.bondRepository = bondRepository;
        this.shareRepository = shareRepository;
        this.instrumentRepository = instrumentRepository;
        this.bondAnalyzer = bondAnalyzer;
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
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("secId"));
        Page<InstrumentDto> dtoPage = instrumentRepository
                .findAll(InstrumentSpecifications.fromCriteria(criteria), effective)
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
