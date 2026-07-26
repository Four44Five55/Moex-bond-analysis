package org.example.application;

import org.example.domain.model.CashMovement;
import org.example.infrastructure.persistence.CashMovementRepository;
import org.example.web.dto.portfolio.CashMovementRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Ведение пополнений и выводов по счёту. */
@Service
public class CashMovementService {

    private final CashMovementRepository cashMovementRepository;
    private final AccountService accountService;

    public CashMovementService(CashMovementRepository cashMovementRepository,
                               AccountService accountService) {
        this.cashMovementRepository = cashMovementRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<CashMovement> list(String clientId, Long accountId) {
        accountService.getOwned(clientId, accountId);
        return cashMovementRepository.findByAccountIdOrderByMovedOnAscIdAsc(accountId);
    }

    @Transactional
    public CashMovement add(String clientId, Long accountId, CashMovementRequest req) {
        accountService.getOwned(clientId, accountId);
        CashMovement movement = new CashMovement();
        movement.setAccountId(accountId);
        movement.setDirection(req.direction());
        movement.setMovedOn(req.movedOn());
        movement.setAmount(req.amount());
        movement.setNote(req.note());
        return cashMovementRepository.save(movement);
    }

    @Transactional
    public void delete(String clientId, Long accountId, Long movementId) {
        accountService.getOwned(clientId, accountId);
        CashMovement movement = cashMovementRepository.findById(movementId)
                .filter(m -> m.getAccountId().equals(accountId))
                .orElseThrow(() -> new NotFoundException("Движение денег не найдено: " + movementId));
        cashMovementRepository.delete(movement);
    }
}
