package org.example.web.dto.portfolio;

import org.example.domain.portfolio.defaults.DefaultLoss;

import java.math.BigDecimal;

/**
 * Потери от дефолта в деньгах. Смысл полей — в {@link DefaultLoss}; производные величины
 * посчитаны здесь же, чтобы фронтенд не повторял формулы.
 */
public record DefaultLossDto(
        BigDecimal quantity,
        BigDecimal invested,
        BigDecimal saleProceeds,
        BigDecimal couponsReceived,
        BigDecimal principalReceived,
        BigDecimal recovered,
        BigDecimal marketValue,
        BigDecimal missedCoupons,
        BigDecimal missedPrincipal,
        BigDecimal futureCoupons,
        BigDecimal futurePrincipal,
        BigDecimal capitalReturned,
        BigDecimal capitalLoss,
        BigDecimal netResult,
        double recoveryRatePct
) {

    public static DefaultLossDto from(DefaultLoss l) {
        return new DefaultLossDto(l.quantity(), l.invested(), l.saleProceeds(), l.couponsReceived(),
                l.principalReceived(), l.recovered(), l.marketValue(), l.missedCoupons(),
                l.missedPrincipal(), l.futureCoupons(), l.futurePrincipal(),
                l.capitalReturned(), l.capitalLoss(), l.netResult(), l.recoveryRatePct());
    }
}
