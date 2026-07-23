package org.example.domain.port;

import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.DividendPayment;

import java.util.List;

/** Порт получения корпоративных действий (купоны, амортизации, дивиденды) из внешнего источника. */
public interface CorporateActionsProvider {

    List<CouponPayment> coupons(String secId);

    List<AmortizationPayment> amortizations(String secId);

    List<DividendPayment> dividends(String secId);
}
