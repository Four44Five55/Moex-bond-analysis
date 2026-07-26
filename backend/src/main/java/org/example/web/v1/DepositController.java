package org.example.web.v1;

import jakarta.validation.Valid;
import org.example.application.DepositService;
import org.example.web.dto.portfolio.DepositDto;
import org.example.web.dto.portfolio.DepositRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Банковские вклады клиента (владение по заголовку X-Client-Id). */
@RestController
@RequestMapping("/api/v1/deposits")
public class DepositController {

    private static final String CLIENT = "X-Client-Id";

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @GetMapping
    public List<DepositDto> list(@RequestHeader(CLIENT) String clientId) {
        return depositService.list(clientId);
    }

    @PostMapping
    public DepositDto create(@RequestHeader(CLIENT) String clientId,
                             @Valid @RequestBody DepositRequest req) {
        return depositService.create(clientId, req);
    }

    @PutMapping("/{id}")
    public DepositDto update(@RequestHeader(CLIENT) String clientId,
                             @PathVariable Long id,
                             @Valid @RequestBody DepositRequest req) {
        return depositService.update(clientId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(CLIENT) String clientId, @PathVariable Long id) {
        depositService.delete(clientId, id);
        return ResponseEntity.noContent().build();
    }
}
