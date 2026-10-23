package org.example.application;

import org.example.domain.model.Bond;
import org.example.domain.model.BondDefaultFlags;
import org.example.domain.model.BondDefaultMark;
import org.example.domain.model.DefaultRecovery;
import org.example.domain.model.Instrument;
import org.example.domain.port.BondDefaultProvider;
import org.example.domain.portfolio.defaults.DefaultPolicy;
import org.example.domain.portfolio.defaults.DefaultState;
import org.example.infrastructure.persistence.BondDefaultMarkRepository;
import org.example.infrastructure.persistence.DefaultRecoveryRepository;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.web.dto.portfolio.DefaultMarkRequest;
import org.example.web.dto.portfolio.DefaultRecoveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Дефолты облигаций: признаки биржи, отметки клиента о прекращении выплат и полученное
 * после дефолта возмещение. Отсюда портфель узнаёт, каким платежам графика MOEX верить.
 */
@Service
public class BondDefaultService {

    private static final Logger log = LoggerFactory.getLogger(BondDefaultService.class);

    private final BondDefaultProvider defaultProvider;
    private final BondDefaultMarkRepository markRepository;
    private final DefaultRecoveryRepository recoveryRepository;
    private final InstrumentRepository instrumentRepository;
    private final AccountService accountService;

    public BondDefaultService(BondDefaultProvider defaultProvider,
                              BondDefaultMarkRepository markRepository,
                              DefaultRecoveryRepository recoveryRepository,
                              InstrumentRepository instrumentRepository,
                              AccountService accountService) {
        this.defaultProvider = defaultProvider;
        this.markRepository = markRepository;
        this.recoveryRepository = recoveryRepository;
        this.instrumentRepository = instrumentRepository;
        this.accountService = accountService;
    }

    /**
     * Признаки дефолта бумаги: справочник плюс описание MOEX. Описание недоступно — остаётся
     * справочник: из-за сбоя биржи портфель не должен снова начать ждать купонов от банкрота.
     */
    public BondDefaultFlags flags(Bond bond) {
        BondDefaultFlags known = BondDefaultFlags.of(bond);
        try {
            return known.or(defaultProvider.flags(bond.getSecId()));
        } catch (RuntimeException e) {
            log.warn("Признаки дефолта по {} недоступны: {}", bond.getSecId(), e.getMessage());
            return known;
        }
    }

    /**
     * Дефолтное положение облигаций клиента. В политику попадают бумаги с отметкой клиента
     * или с любым признаком MOEX; остальные платят по графику.
     */
    @Transactional(readOnly = true)
    public DefaultPolicy policyFor(String clientId, Collection<? extends Instrument> instruments) {
        Map<String, BondDefaultMark> marks = marksBySecId(clientId);
        LocalDate today = LocalDate.now();
        Map<String, DefaultState> states = new HashMap<>();
        for (Instrument instrument : instruments) {
            if (!(instrument instanceof Bond bond)) {
                continue;
            }
            BondDefaultFlags moex = flags(bond);
            BondDefaultMark mark = marks.get(bond.getSecId());
            if (mark != null || moex.any()) {
                states.put(bond.getSecId(), DefaultState.of(moex,
                        mark == null ? null : mark.getPaymentsStoppedOn(),
                        mark == null ? null : mark.getNote(), today));
            }
        }
        return new DefaultPolicy(states);
    }

    private Map<String, BondDefaultMark> marksBySecId(String clientId) {
        return markRepository.findByClientId(clientId).stream()
                .collect(Collectors.toMap(BondDefaultMark::getSecId, Function.identity()));
    }

    /** Отметить, с какой даты по бумаге не поступают выплаты; повторная отметка заменяет прежнюю. */
    @Transactional
    public BondDefaultMark mark(String clientId, String secId, DefaultMarkRequest req) {
        Instrument instrument = instrumentRepository.findBySecId(secId)
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + secId));
        if (!(instrument instanceof Bond)) {
            throw new IllegalArgumentException("Дефолт отмечается только по облигациям: " + secId);
        }
        BondDefaultMark mark = markRepository.findByClientIdAndSecId(clientId, secId)
                .orElseGet(BondDefaultMark::new);
        mark.setClientId(clientId);
        mark.setSecId(secId);
        mark.setPaymentsStoppedOn(req.paymentsStoppedOn());
        mark.setNote(req.note());
        return markRepository.save(mark);
    }

    @Transactional
    public void unmark(String clientId, String secId) {
        markRepository.findByClientIdAndSecId(clientId, secId).ifPresent(markRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<DefaultRecovery> recoveries(String clientId, Long accountId) {
        accountService.getOwned(clientId, accountId);
        return recoveryRepository.findByAccountIdOrderByReceivedOnAscIdAsc(accountId);
    }

    @Transactional
    public DefaultRecovery addRecovery(String clientId, Long accountId, DefaultRecoveryRequest req) {
        accountService.getOwned(clientId, accountId);
        DefaultRecovery recovery = new DefaultRecovery();
        recovery.setAccountId(accountId);
        recovery.setSecId(req.secId());
        recovery.setReceivedOn(req.receivedOn());
        recovery.setAmount(req.amount());
        recovery.setNote(req.note());
        return recoveryRepository.save(recovery);
    }

    @Transactional
    public void deleteRecovery(String clientId, Long accountId, Long recoveryId) {
        accountService.getOwned(clientId, accountId);
        DefaultRecovery recovery = recoveryRepository.findById(recoveryId)
                .filter(r -> r.getAccountId().equals(accountId))
                .orElseThrow(() -> new NotFoundException("Возмещение не найдено: " + recoveryId));
        recoveryRepository.delete(recovery);
    }
}
