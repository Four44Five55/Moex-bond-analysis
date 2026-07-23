package org.example.web.mapper;

import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.model.Share;
import org.example.web.dto.InstrumentDto;

/** Статический маппер доменных инструментов в DTO (без внешних зависимостей). */
public final class InstrumentMapper {

    private InstrumentMapper() {
    }

    public static InstrumentDto toDto(Instrument instrument) {
        if (instrument instanceof Bond bond) {
            return fromBond(bond);
        }
        if (instrument instanceof Share share) {
            return fromShare(share);
        }
        throw new IllegalArgumentException("Неизвестный тип инструмента: " + instrument.getClass());
    }

    private static InstrumentDto fromShare(Share s) {
        return new InstrumentDto(
                s.getSecId(),
                s.getType(),
                s.getShortName(),
                s.getFullName(),
                s.getIsin(),
                s.getCurrency(),
                s.getBoard(),
                s.getMarket(),
                s.getLastPrice(),
                s.getPrevPrice(),
                s.getDayChangePct(),
                s.getTradeVolume(),
                // облигационные — не применимо
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                // акционные
                s.getLotSize(),
                s.getCapitalization(),
                s.getSector(),
                s.getListLevel()
        );
    }

    private static InstrumentDto fromBond(Bond b) {
        return new InstrumentDto(
                b.getSecId(),
                b.getType(),
                b.getShortName(),
                b.getFullName(),
                b.getIsin(),
                b.getCurrency(),
                b.getBoard(),
                b.getMarket(),
                b.getLastPrice(),
                b.getPrevPrice(),
                b.getDayChangePct(),
                b.getTradeVolume(),
                // облигационные
                b.getFaceValue(),
                b.getCouponRate(),
                b.getYieldToMaturity(),
                b.getDuration(),
                b.getConvexity(),
                b.getCurrentPrice(),
                b.getAnnualCoupon(),
                b.getCouponFrequency(),
                b.getMaturityDate(),
                b.getNextCouponDate(),
                b.getIssueDate(),
                b.getIssuerRating(),
                b.getReliabilityScore(),
                b.isInvestmentGrade(),
                b.isCallable(),
                b.isPuttable(),
                b.isSuspiciousYield(),
                // акционные — не применимо
                null, null, null,
                b.getListLevel()
        );
    }
}
