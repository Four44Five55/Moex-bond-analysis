package org.example.infrastructure.persistence;

import org.example.domain.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByClientIdOrderByAddedAtDesc(String clientId);

    boolean existsByClientIdAndSecId(String clientId, String secId);

    void deleteByClientIdAndSecId(String clientId, String secId);
}
