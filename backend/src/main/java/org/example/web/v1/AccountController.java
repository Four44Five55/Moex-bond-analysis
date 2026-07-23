package org.example.web.v1;

import jakarta.validation.Valid;
import org.example.application.AccountService;
import org.example.web.dto.portfolio.AccountDto;
import org.example.web.dto.portfolio.AccountRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Управление инвестиционными счетами (владение по заголовку X-Client-Id). */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final String CLIENT = "X-Client-Id";

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountDto> list(@RequestHeader(CLIENT) String clientId) {
        return accountService.list(clientId).stream().map(AccountDto::from).toList();
    }

    @GetMapping("/{id}")
    public AccountDto get(@RequestHeader(CLIENT) String clientId, @PathVariable Long id) {
        return AccountDto.from(accountService.getOwned(clientId, id));
    }

    @PostMapping
    public AccountDto create(@RequestHeader(CLIENT) String clientId,
                             @Valid @RequestBody AccountRequest req) {
        return AccountDto.from(accountService.create(clientId, req));
    }

    @PutMapping("/{id}")
    public AccountDto update(@RequestHeader(CLIENT) String clientId,
                            @PathVariable Long id,
                            @Valid @RequestBody AccountRequest req) {
        return AccountDto.from(accountService.update(clientId, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(CLIENT) String clientId, @PathVariable Long id) {
        accountService.delete(clientId, id);
        return ResponseEntity.noContent().build();
    }
}
