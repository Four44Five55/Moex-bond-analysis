package org.example.web.dto;

import org.example.domain.model.InstrumentType;

import java.time.LocalDate;

/**
 * Единое плоское представление инструмента для фронтенда.
 * Поля, специфичные для типа, необязательны (null для другого типа).
 */
public record InstrumentDto(
        // Общие
        String secId,
        InstrumentType type,
        String shortName,
        String fullName,
        String isin,
        String currency,
        String board,
        String market,
        Double lastPrice,
        Double prevPrice,
        Double dayChangePct,
        Double tradeVolume,

        // Облигации
        Double faceValue,
        Double couponRate,
        Double yieldToMaturity,
        Double duration,
        Double convexity,
        Double currentPrice,
        Double annualCoupon,
        Integer couponFrequency,
        LocalDate maturityDate,
        LocalDate nextCouponDate,
        LocalDate issueDate,
        String issuerRating,
        Integer reliabilityScore,
        Boolean investmentGrade,
        Boolean callable,
        Boolean puttable,
        Boolean suspiciousYield,

        // Акции
        Integer lotSize,
        Double capitalization,
        String sector,
        Integer listLevel
) {
}
