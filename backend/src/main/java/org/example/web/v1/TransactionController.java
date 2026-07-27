package org.example.web.v1;

import jakarta.validation.Valid;
import org.example.application.TransactionService;
import org.example.web.dto.portfolio.AciBackfillDto;
import org.example.web.dto.portfolio.AddTransactionRequest;
import org.example.web.dto.portfolio.TransactionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transactions")
public class TransactionController {

    private static final String CLIENT = "X-Client-Id";

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionDto> list(@RequestHeader(CLIENT) String clientId,
                                     @PathVariable Long accountId) {
        return transactionService.list(clientId, accountId);
    }

    @PostMapping
    public TransactionDto add(@RequestHeader(CLIENT) String clientId,
                              @PathVariable Long accountId,
                              @Valid @RequestBody AddTransactionRequest req) {
        return transactionService.add(clientId, accountId, req);
    }

    /**
     * Дозаполнить НКД в сделках по облигациям, заведённых до появления автоподстановки.
     * По умолчанию — примерка: возвращает, что изменится, не сохраняя. Запись включается
     * явным {@code dryRun=false}, потому что НКД меняет себестоимость, налог и остаток денег.
     */
    @PostMapping("/backfill-aci")
    public AciBackfillDto backfillAci(@RequestHeader(CLIENT) String clientId,
                                      @PathVariable Long accountId,
                                      @RequestParam(defaultValue = "true") boolean dryRun) {
        return transactionService.backfillAccruedInterest(clientId, accountId, dryRun);
    }

    @DeleteMapping("/{txId}")
    public ResponseEntity<Void> delete(@RequestHeader(CLIENT) String clientId,
                                       @PathVariable Long accountId,
                                       @PathVariable Long txId) {
        transactionService.delete(clientId, accountId, txId);
        return ResponseEntity.noContent().build();
    }
}
