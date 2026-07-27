package org.example.web.v1;

import org.example.application.PortfolioService;
import org.example.web.dto.portfolio.ConsolidatedOverviewDto;
import org.example.web.dto.portfolio.PaymentCalendarDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Сводка по всем счетам клиента. Вынесена из {@code /accounts}, потому что путь
 * {@code /accounts/overview} конкурировал бы с {@code /accounts/{id}}: разрешение таких
 * конфликтов держится на приоритете литерала над переменной пути и ломается от одной опечатки.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class ConsolidatedPortfolioController {

    private static final String CLIENT = "X-Client-Id";

    private final PortfolioService portfolioService;

    public ConsolidatedPortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/overview")
    public ConsolidatedOverviewDto overview(@RequestHeader(CLIENT) String clientId) {
        return portfolioService.consolidated(clientId);
    }

    /** Календарь выплат по всему капиталу: помесячные суммы за months месяцев вперёд и сами выплаты. */
    @GetMapping("/calendar")
    public PaymentCalendarDto calendar(@RequestHeader(CLIENT) String clientId,
                                       @RequestParam(defaultValue = "12") int months) {
        return portfolioService.calendar(clientId, months);
    }
}
