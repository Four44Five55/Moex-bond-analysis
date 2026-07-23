package org.example.domain.portfolio.tax;

import org.example.domain.model.Account;
import org.example.domain.model.AccountType;
import org.example.domain.model.IisType;
import org.springframework.stereotype.Component;

/** Выбирает налоговую стратегию по типу счёта (Abstract Factory для стратегий). */
@Component
public class TaxStrategyFactory {

    private final BrokerageTaxStrategy brokerage;
    private final IisTypeATaxStrategy iisA;
    private final IisTypeBTaxStrategy iisB;

    public TaxStrategyFactory(BrokerageTaxStrategy brokerage,
                              IisTypeATaxStrategy iisA,
                              IisTypeBTaxStrategy iisB) {
        this.brokerage = brokerage;
        this.iisA = iisA;
        this.iisB = iisB;
    }

    public TaxStrategy forAccount(Account account) {
        if (account.getType() == AccountType.IIS) {
            return account.getIisType() == IisType.B ? iisB : iisA;
        }
        return brokerage;
    }
}
