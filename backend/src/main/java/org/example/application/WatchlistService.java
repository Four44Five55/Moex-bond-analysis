package org.example.application;

import org.example.domain.model.Instrument;
import org.example.domain.model.WatchlistItem;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.WatchlistRepository;
import org.example.web.dto.InstrumentDto;
import org.example.web.mapper.InstrumentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Анонимное избранное: элементы привязаны к клиентскому идентификатору (заголовок X-Client-Id).
 */
@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final InstrumentRepository instrumentRepository;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            InstrumentRepository instrumentRepository) {
        this.watchlistRepository = watchlistRepository;
        this.instrumentRepository = instrumentRepository;
    }

    /** Инструменты из избранного клиента в порядке добавления (свежие сверху). */
    @Transactional(readOnly = true)
    public List<InstrumentDto> list(String clientId) {
        List<String> secIds = watchlistRepository.findByClientIdOrderByAddedAtDesc(clientId).stream()
                .map(WatchlistItem::getSecId)
                .toList();
        if (secIds.isEmpty()) {
            return List.of();
        }
        Map<String, Instrument> bySecId = instrumentRepository.findAll().stream()
                .filter(i -> secIds.contains(i.getSecId()))
                .collect(Collectors.toMap(Instrument::getSecId, Function.identity()));

        return secIds.stream()
                .map(bySecId::get)
                .filter(java.util.Objects::nonNull)
                .map(InstrumentMapper::toDto)
                .toList();
    }

    @Transactional
    public void add(String clientId, String secId) {
        if (instrumentRepository.findBySecId(secId).isEmpty()) {
            throw new NotFoundException("Инструмент не найден: " + secId);
        }
        if (!watchlistRepository.existsByClientIdAndSecId(clientId, secId)) {
            WatchlistItem item = new WatchlistItem();
            item.setClientId(clientId);
            item.setSecId(secId);
            watchlistRepository.save(item);
        }
    }

    @Transactional
    public void remove(String clientId, String secId) {
        watchlistRepository.deleteByClientIdAndSecId(clientId, secId);
    }
}
