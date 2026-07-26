package org.example.web.v1;

import org.example.application.PortfolioService;
import org.example.domain.portfolio.tax.TaxBreakdown;
import org.example.web.dto.portfolio.AllocationSliceDto;
import org.example.web.dto.portfolio.IncomeEventDto;
import org.example.web.dto.portfolio.PortfolioHistoryDto;
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

    /** Фактически полученные выплаты (прошлое, с учётом позиции на дату отсечки). */
    @GetMapping("/received")
    public List<IncomeEventDto> received(@RequestHeader(CLIENT) String clientId,
                                         @PathVariable Long accountId) {
        return portfolioService.received(clientId, accountId);
    }

    @GetMapping("/tax")
    public TaxBreakdown tax(@RequestHeader(CLIENT) String clientId,
                            @PathVariable Long accountId) {
        return portfolioService.tax(clientId, accountId);
    }

    /** Дневной ряд стоимости портфеля за последние months месяцев и годовая доходность XIRR. */
    @GetMapping("/value-series")
    public PortfolioHistoryDto valueSeries(@RequestHeader(CLIENT) String clientId,
                                           @PathVariable Long accountId,
                                           @RequestParam(defaultValue = "12") int months) {
        return portfolioService.valueSeries(clientId, accountId, months);
    }

    /** Аллокация портфеля по измерению: by = type | sector | currency. */
    @GetMapping("/allocation")
    public List<AllocationSliceDto> allocation(@RequestHeader(CLIENT) String clientId,
                                               @PathVariable Long accountId,
                                               @RequestParam(defaultValue = "type") String by) {
        return portfolioService.allocation(clientId, accountId, by);
    }
}
