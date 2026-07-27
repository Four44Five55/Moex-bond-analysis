package org.example.application;

import org.example.application.portfolio.InstrumentPricing;
import org.example.domain.model.AccruedInterest;
import org.example.domain.model.Account;
import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.model.Transaction;
import org.example.domain.port.BondHistoryProvider;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.TransactionRepository;
import org.example.web.dto.portfolio.AciBackfillDto;
import org.example.web.dto.portfolio.AddTransactionRequest;
import org.example.web.dto.portfolio.TransactionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Ведение сделок по счёту; расчёт комиссии из % счёта, если не задана явно. */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final AccountService accountService;
    private final BondHistoryProvider bondHistory;

    public TransactionService(TransactionRepository transactionRepository,
                              InstrumentRepository instrumentRepository,
                              AccountService accountService,
                              BondHistoryProvider bondHistory) {
        this.transactionRepository = transactionRepository;
        this.instrumentRepository = instrumentRepository;
        this.accountService = accountService;
        this.bondHistory = bondHistory;
    }

    /** Сделки счёта с названиями бумаг: по одному коду в списке не понять, что за выпуск. */
    @Transactional(readOnly = true)
    public List<TransactionDto> list(String clientId, Long accountId) {
        accountService.getOwned(clientId, accountId);
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(accountId);
        Map<String, Instrument> instruments = instrumentRepository
                .findAllBySecIdIn(txns.stream().map(Transaction::getSecId).distinct().toList())
                .stream().collect(Collectors.toMap(Instrument::getSecId, i -> i));

        return txns.stream()
                .map(t -> TransactionDto.from(t, shortName(instruments.get(t.getSecId()))))
                .toList();
    }

    /** Инструмент мог исчезнуть из справочника (погашен, делистинг) — сделка от этого не пропадает. */
    private static String shortName(Instrument instrument) {
        return instrument == null ? null : instrument.getShortName();
    }

    @Transactional
    public TransactionDto add(String clientId, Long accountId, AddTransactionRequest req) {
        Account account = accountService.getOwned(clientId, accountId);
        Instrument instrument = instrumentRepository.findBySecId(req.secId())
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + req.secId()));

        // Комиссия считается от полного объёма сделки, включая НКД: брокер берёт её с оборота
        double notional = InstrumentPricing.tradeMoneyPerUnit(instrument, req.price(), req.aci())
                * req.quantity();
        double commission = req.commission() != null
                ? req.commission()
                : notional * account.getDefaultCommissionPct() / 100.0;

        Transaction txn = new Transaction();
        txn.setAccountId(accountId);
        txn.setSecId(req.secId());
        txn.setSide(req.side());
        txn.setTradeDate(req.tradeDate());
        txn.setQuantity(req.quantity());
        txn.setPrice(req.price());
        txn.setAci(req.aci());
        txn.setCommission(commission);
        txn.setNote(req.note());
        return TransactionDto.from(transactionRepository.save(txn), shortName(instrument));
    }

    /**
     * Дозаполнение НКД в ранее заведённых сделках по облигациям: до появления автоподстановки
     * поле оставалось нулём, а от него зависят себестоимость позиции, налоговая база и остаток денег.
     * <p>
     * Трогаем только сделки с {@code aci == 0}: там, где пользователь вписал значение сам,
     * биржевое ничего не улучшит, а перезапись потеряла бы его правку. Ноль как «честный» НКД
     * (покупка в день выплаты купона) от этого не страдает — история вернёт примерно ноль.
     * <p>
     * Сделки без ответа биржи остаются нетронутыми и попадают в счётчик {@code unknown}:
     * молча оставить ноль там, где НКД был, — то же самое искажение, только незаметное.
     *
     * @param dryRun только показать, что изменится, ничего не сохраняя
     */
    @Transactional
    public AciBackfillDto backfillAccruedInterest(String clientId, Long accountId, boolean dryRun) {
        accountService.getOwned(clientId, accountId);
        List<Transaction> txns = transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(accountId);

        Map<String, Instrument> instruments = instrumentRepository
                .findAllBySecIdIn(txns.stream().map(Transaction::getSecId).distinct().toList())
                .stream().collect(Collectors.toMap(Instrument::getSecId, i -> i));

        List<AciBackfillDto.Row> rows = new ArrayList<>();
        int candidates = 0;
        int unknown = 0;
        double costDelta = 0;

        for (Transaction txn : txns) {
            if (txn.getAci() != 0 || !(instruments.get(txn.getSecId()) instanceof Bond)) {
                continue;
            }
            candidates++;
            AccruedInterest aci = accruedInterestOrUnknown(txn);
            if (!aci.known() || aci.perBond() <= 0) {
                unknown++;
                continue;
            }
            double amount = aci.perBond() * txn.getQuantity();
            costDelta += amount;
            rows.add(new AciBackfillDto.Row(txn.getId(), txn.getSecId(),
                    shortName(instruments.get(txn.getSecId())), txn.getTradeDate(),
                    txn.getSide().name(), txn.getQuantity(), aci.perBond(), aci.tradeDate(), amount));
            if (!dryRun) {
                txn.setAci(aci.perBond());
            }
        }
        if (!dryRun && !rows.isEmpty()) {
            log.info("Счёт {}: НКД дозаполнен в {} сделках на {} ₽", accountId, rows.size(), costDelta);
        }
        return new AciBackfillDto(dryRun, candidates, rows.size(), unknown, costDelta, rows);
    }

    /** Сбой биржи по одной сделке не должен ронять весь пересчёт. */
    private AccruedInterest accruedInterestOrUnknown(Transaction txn) {
        try {
            return bondHistory.accruedInterestOn(txn.getSecId(), txn.getTradeDate());
        } catch (RuntimeException e) {
            log.warn("НКД по сделке {} ({} на {}) недоступен: {}",
                    txn.getId(), txn.getSecId(), txn.getTradeDate(), e.getMessage());
            return AccruedInterest.unknown();
        }
    }

    @Transactional
    public void delete(String clientId, Long accountId, Long txId) {
        accountService.getOwned(clientId, accountId);
        Transaction txn = transactionRepository.findByIdAndAccountId(txId, accountId)
                .orElseThrow(() -> new NotFoundException("Сделка не найдена: " + txId));
        transactionRepository.delete(txn);
    }
}
