package org.example.web.v1;

import jakarta.validation.Valid;
import org.example.application.BondDefaultService;
import org.example.web.dto.portfolio.DefaultRecoveryDto;
import org.example.web.dto.portfolio.DefaultRecoveryRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Возмещение по бумагам в дефолте, полученное на счёт. */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/recoveries")
public class RecoveryController {

    private static final String CLIENT = "X-Client-Id";

    private final BondDefaultService bondDefaultService;

    public RecoveryController(BondDefaultService bondDefaultService) {
        this.bondDefaultService = bondDefaultService;
    }

    @GetMapping
    public List<DefaultRecoveryDto> list(@RequestHeader(CLIENT) String clientId,
                                         @PathVariable Long accountId) {
        return bondDefaultService.recoveries(clientId, accountId).stream()
                .map(DefaultRecoveryDto::from).toList();
    }

    @PostMapping
    public DefaultRecoveryDto add(@RequestHeader(CLIENT) String clientId,
                                  @PathVariable Long accountId,
                                  @Valid @RequestBody DefaultRecoveryRequest req) {
        return DefaultRecoveryDto.from(bondDefaultService.addRecovery(clientId, accountId, req));
    }

    @DeleteMapping("/{recoveryId}")
    public ResponseEntity<Void> delete(@RequestHeader(CLIENT) String clientId,
                                       @PathVariable Long accountId,
                                       @PathVariable Long recoveryId) {
        bondDefaultService.deleteRecovery(clientId, accountId, recoveryId);
        return ResponseEntity.noContent().build();
    }
}
