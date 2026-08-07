package org.example.application;

import org.example.domain.model.Account;
import org.example.domain.model.Deposit;
import org.example.domain.money.Money;
import org.example.domain.portfolio.deposit.DepositCalculator;
import org.example.domain.portfolio.deposit.InterestAccrual;
import org.example.infrastructure.persistence.DepositRepository;
import org.example.web.dto.portfolio.DepositDto;
import org.example.web.dto.portfolio.DepositRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Ведение банковских вкладов клиента и их представление с посчитанными величинами. */
@Service
public class DepositService {

    private final DepositRepository depositRepository;
    private final DepositCalculator depositCalculator;
    private final AccountService accountService;

    public DepositService(DepositRepository depositRepository, DepositCalculator depositCalculator,
                          AccountService accountService) {
        this.depositRepository = depositRepository;
        this.depositCalculator = depositCalculator;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<Deposit> entities(String clientId) {
        return depositRepository.findByClientIdOrderByOpenedOnAscIdAsc(clientId);
    }

    @Transactional(readOnly = true)
    public List<DepositDto> list(String clientId) {
        LocalDate today = LocalDate.now();
        Map<Long, String> accountNames = accountNames(clientId);
        return entities(clientId).stream().map(d -> toDto(d, today, accountNames)).toList();
    }

    @Transactional
    public DepositDto create(String clientId, DepositRequest req) {
        Deposit deposit = new Deposit();
        deposit.setClientId(clientId);
        apply(deposit, req, clientId);
        return toDto(depositRepository.save(deposit), LocalDate.now(), accountNames(clientId));
    }

    @Transactional
    public DepositDto update(String clientId, Long id, DepositRequest req) {
        Deposit deposit = getOwned(clientId, id);
        apply(deposit, req, clientId);
        return toDto(depositRepository.save(deposit), LocalDate.now(), accountNames(clientId));
    }

    /** Названия счетов клиента: вклад ссылается на счёт-источник только идентификатором. */
    private Map<Long, String> accountNames(String clientId) {
        return accountService.list(clientId).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
    }

    @Transactional
    public void delete(String clientId, Long id) {
        depositRepository.delete(getOwned(clientId, id));
    }

    private Deposit getOwned(String clientId, Long id) {
        return depositRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new NotFoundException("Вклад не найден: " + id));
    }

    private void apply(Deposit deposit, DepositRequest req, String clientId) {
        if (!req.maturesOn().isAfter(req.openedOn())) {
            throw new IllegalArgumentException("Дата закрытия вклада должна быть позже даты открытия");
        }
        if (req.sourceAccountId() != null) {
            // Чужой счёт источником быть не может — иначе через вклад можно вычесть деньги
            // с чужого счёта; getOwned бросит, если счёт не принадлежит клиенту
            accountService.getOwned(clientId, req.sourceAccountId());
        }
        deposit.setSourceAccountId(req.sourceAccountId());
        deposit.setName(req.name());
        deposit.setPrincipal(req.principal());
        deposit.setRatePct(req.ratePct());
        deposit.setOpenedOn(req.openedOn());
        deposit.setMaturesOn(req.maturesOn());
        deposit.setCapitalized(req.capitalized());
        deposit.setPayoutPeriod(req.payoutPeriod());
        deposit.setNote(req.note());
    }

    private DepositDto toDto(Deposit d, LocalDate today, Map<Long, String> accountNames) {
        BigDecimal value = depositCalculator.valueOn(d, today);
        boolean matured = !today.isBefore(d.getMaturesOn());
        LocalDate nextPayout = depositCalculator.payouts(d).stream()
                .map(InterestAccrual::date)
                .filter(date -> !date.isBefore(today))
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new DepositDto(d.getId(), d.getName(), d.getPrincipal(), d.getRatePct(),
                d.getOpenedOn(), d.getMaturesOn(), d.isCapitalized(), d.getPayoutPeriod(), d.getNote(),
                Money.round(value), Money.round(value.subtract(d.getPrincipal())),
                Money.round(depositCalculator.totalInterest(d)),
                nextPayout, matured, d.getSourceAccountId(),
                d.getSourceAccountId() == null ? null : accountNames.get(d.getSourceAccountId()));
    }
}
