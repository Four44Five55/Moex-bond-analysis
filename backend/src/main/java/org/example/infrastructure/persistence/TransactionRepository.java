package org.example.infrastructure.persistence;

import org.example.domain.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByTradeDateAscIdAsc(Long accountId);

    Optional<Transaction> findByIdAndAccountId(Long id, Long accountId);
}
