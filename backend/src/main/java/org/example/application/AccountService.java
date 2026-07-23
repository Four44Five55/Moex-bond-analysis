package org.example.application;

import org.example.domain.model.Account;
import org.example.domain.model.AccountType;
import org.example.infrastructure.persistence.AccountRepository;
import org.example.web.dto.portfolio.AccountRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD инвестиционных счетов с проверкой владения по client_id. */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> list(String clientId) {
        return accountRepository.findByClientIdOrderByCreatedAt(clientId);
    }

    @Transactional(readOnly = true)
    public Account getOwned(String clientId, Long id) {
        return accountRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new NotFoundException("Счёт не найден: " + id));
    }

    @Transactional
    public Account create(String clientId, AccountRequest req) {
        Account account = new Account();
        account.setClientId(clientId);
        apply(account, req);
        return accountRepository.save(account);
    }

    @Transactional
    public Account update(String clientId, Long id, AccountRequest req) {
        Account account = getOwned(clientId, id);
        apply(account, req);
        return accountRepository.save(account);
    }

    @Transactional
    public void delete(String clientId, Long id) {
        Account account = getOwned(clientId, id);
        accountRepository.delete(account);
    }

    private void apply(Account account, AccountRequest req) {
        if (req.type() == AccountType.IIS && req.iisType() == null) {
            throw new IllegalArgumentException("Для ИИС необходимо указать тип (A или B)");
        }
        account.setName(req.name());
        account.setType(req.type());
        account.setIisType(req.type() == AccountType.IIS ? req.iisType() : null);
        account.setDefaultCommissionPct(req.defaultCommissionPct());
        account.setBaseTaxRate(req.baseTaxRate() > 0 ? req.baseTaxRate() : 13.0);
    }
}
