package org.example.web.dto.portfolio;

import java.util.List;

/** Потери от дефолтов по всем счетам клиента: построчно и итогом. */
public record DefaultReportDto(
        List<DefaultPositionDto> positions,
        DefaultLossDto totals
) {
}
