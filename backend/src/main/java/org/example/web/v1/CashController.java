package org.example.web.v1;

import jakarta.validation.Valid;
import org.example.application.CashMovementService;
import org.example.web.dto.portfolio.CashMovementDto;
import org.example.web.dto.portfolio.CashMovementRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Пополнения и выводы денег по счёту. */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/cash")
public class CashController {

    private static final String CLIENT = "X-Client-Id";

    private final CashMovementService cashMovementService;

    public CashController(CashMovementService cashMovementService) {
        this.cashMovementService = cashMovementService;
    }

    @GetMapping
    public List<CashMovementDto> list(@RequestHeader(CLIENT) String clientId,
                                      @PathVariable Long accountId) {
        return cashMovementService.list(clientId, accountId).stream().map(CashMovementDto::from).toList();
    }

    @PostMapping
    public CashMovementDto add(@RequestHeader(CLIENT) String clientId,
                               @PathVariable Long accountId,
                               @Valid @RequestBody CashMovementRequest req) {
        return CashMovementDto.from(cashMovementService.add(clientId, accountId, req));
    }

    @DeleteMapping("/{movementId}")
    public ResponseEntity<Void> delete(@RequestHeader(CLIENT) String clientId,
                                       @PathVariable Long accountId,
                                       @PathVariable Long movementId) {
        cashMovementService.delete(clientId, accountId, movementId);
        return ResponseEntity.noContent().build();
    }
}
