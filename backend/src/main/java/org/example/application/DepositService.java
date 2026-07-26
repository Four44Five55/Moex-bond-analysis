package org.example.application;

import org.example.domain.model.Deposit;
import org.example.domain.portfolio.deposit.DepositCalculator;
import org.example.domain.portfolio.deposit.InterestAccrual;
import org.example.infrastructure.persistence.DepositRepository;
import org.example.web.dto.portfolio.DepositDto;
import org.example.web.dto.portfolio.DepositRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Ведение банковских вкладов клиента и их представление с посчитанными величинами. */
@Service
public class DepositService {

    private final DepositRepository depositRepository;
    private final DepositCalculator depositCalculator;

    public DepositService(DepositRepository depositRepository, DepositCalculator depositCalculator) {
        this.depositRepository = depositRepository;
        this.depositCalculator = depositCalculator;
    }

    @Transactional(readOnly = true)
    public List<Deposit> entities(String clientId) {
        return depositRepository.findByClientIdOrderByOpenedOnAscIdAsc(clientId);
    }

    @Transactional(readOnly = true)
    public List<DepositDto> list(String clientId) {
        LocalDate today = LocalDate.now();
        return entities(clientId).stream().map(d -> toDto(d, today)).toList();
    }

    @Transactional
    public DepositDto create(String clientId, DepositRequest req) {
        Deposit deposit = new Deposit();
        deposit.setClientId(clientId);
        apply(deposit, req);
        return toDto(depositRepository.save(deposit), LocalDate.now());
    }

    @Transactional
    public DepositDto update(String clientId, Long id, DepositRequest req) {
        Deposit deposit = getOwned(clientId, id);
        apply(deposit, req);
        return toDto(depositRepository.save(deposit), LocalDate.now());
    }

    @Transactional
    public void delete(String clientId, Long id) {
        depositRepository.delete(getOwned(clientId, id));
    }

    private Deposit getOwned(String clientId, Long id) {
        return depositRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new NotFoundException("Вклад не найден: " + id));
    }

    private void apply(Deposit deposit, DepositRequest req) {
        if (!req.maturesOn().isAfter(req.openedOn())) {
            throw new IllegalArgumentException("Дата закрытия вклада должна быть позже даты открытия");
        }
        deposit.setName(req.name());
        deposit.setPrincipal(req.principal());
        deposit.setRatePct(req.ratePct());
        deposit.setOpenedOn(req.openedOn());
        deposit.setMaturesOn(req.maturesOn());
        deposit.setCapitalized(req.capitalized());
        deposit.setPayoutPeriod(req.payoutPeriod());
        deposit.setNote(req.note());
    }

    private DepositDto toDto(Deposit d, LocalDate today) {
        double value = depositCalculator.valueOn(d, today);
        boolean matured = !today.isBefore(d.getMaturesOn());
        LocalDate nextPayout = depositCalculator.payouts(d).stream()
                .map(InterestAccrual::date)
                .filter(date -> !date.isBefore(today))
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new DepositDto(d.getId(), d.getName(), d.getPrincipal(), d.getRatePct(),
                d.getOpenedOn(), d.getMaturesOn(), d.isCapitalized(), d.getPayoutPeriod(), d.getNote(),
                value, value - d.getPrincipal(), depositCalculator.totalInterest(d),
                nextPayout, matured);
    }
}
