package org.example.application;

import org.example.application.portfolio.InstrumentPricing;
import org.example.domain.model.Account;
import org.example.domain.model.Instrument;
import org.example.domain.model.Transaction;
import org.example.infrastructure.persistence.InstrumentRepository;
import org.example.infrastructure.persistence.TransactionRepository;
import org.example.web.dto.portfolio.AddTransactionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Ведение сделок по счёту; расчёт комиссии из % счёта, если не задана явно. */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final AccountService accountService;

    public TransactionService(TransactionRepository transactionRepository,
                              InstrumentRepository instrumentRepository,
                              AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.instrumentRepository = instrumentRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<Transaction> list(String clientId, Long accountId) {
        accountService.getOwned(clientId, accountId);
        return transactionRepository.findByAccountIdOrderByTradeDateAscIdAsc(accountId);
    }

    @Transactional
    public Transaction add(String clientId, Long accountId, AddTransactionRequest req) {
        Account account = accountService.getOwned(clientId, accountId);
        Instrument instrument = instrumentRepository.findBySecId(req.secId())
                .orElseThrow(() -> new NotFoundException("Инструмент не найден: " + req.secId()));

        double notional = InstrumentPricing.tradeMoneyPerUnit(instrument, req.price(), 0.0) * req.quantity();
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
        return transactionRepository.save(txn);
    }

    @Transactional
    public void delete(String clientId, Long accountId, Long txId) {
        accountService.getOwned(clientId, accountId);
        Transaction txn = transactionRepository.findByIdAndAccountId(txId, accountId)
                .orElseThrow(() -> new NotFoundException("Сделка не найдена: " + txId));
        transactionRepository.delete(txn);
    }
}
