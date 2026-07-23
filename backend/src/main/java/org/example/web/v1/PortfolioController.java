package org.example.web.v1;

import org.example.application.PortfolioService;
import org.example.domain.portfolio.tax.TaxBreakdown;
import org.example.web.dto.portfolio.IncomeEventDto;
import org.example.web.dto.portfolio.PortfolioSummaryDto;
import org.example.web.dto.portfolio.PositionDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Аналитика портфеля: позиции, сводка, доходный календарь, налоги. */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class PortfolioController {

    private static final String CLIENT = "X-Client-Id";

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/positions")
    public List<PositionDto> positions(@RequestHeader(CLIENT) String clientId,
                                       @PathVariable Long accountId) {
        return portfolioService.positions(clientId, accountId);
    }

    @GetMapping("/summary")
    public PortfolioSummaryDto summary(@RequestHeader(CLIENT) String clientId,
                                       @PathVariable Long accountId) {
        return portfolioService.summary(clientId, accountId);
    }

    @GetMapping("/income")
    public List<IncomeEventDto> income(@RequestHeader(CLIENT) String clientId,
                                       @PathVariable Long accountId) {
        return portfolioService.income(clientId, accountId);
    }

    @GetMapping("/tax")
    public TaxBreakdown tax(@RequestHeader(CLIENT) String clientId,
                            @PathVariable Long accountId) {
        return portfolioService.tax(clientId, accountId);
    }
}
