package org.example.domain.port;

import org.example.domain.model.Bond;
import org.example.domain.model.Share;

import java.util.List;

/**
 * Порт доступа к рыночным данным (hexagonal). Реализуется адаптером MOEX,
 * но домен от конкретного источника не зависит.
 */
public interface MarketDataProvider {

    List<Bond> fetchBonds();

    List<Share> fetchShares();
}
