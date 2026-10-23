package org.example.web.v1;

import jakarta.validation.Valid;
import org.example.application.BondDefaultService;
import org.example.application.PortfolioService;
import org.example.web.dto.portfolio.ConsolidatedOverviewDto;
import org.example.web.dto.portfolio.DefaultMarkRequest;
import org.example.web.dto.portfolio.DefaultReportDto;
import org.example.web.dto.portfolio.PaymentCalendarDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final BondDefaultService bondDefaultService;

    public ConsolidatedPortfolioController(PortfolioService portfolioService,
                                           BondDefaultService bondDefaultService) {
        this.portfolioService = portfolioService;
        this.bondDefaultService = bondDefaultService;
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

    /** Потери от дефолтов по всем счетам: вложено, возвращено, недополучено. */
    @GetMapping("/defaults")
    public DefaultReportDto defaults(@RequestHeader(CLIENT) String clientId) {
        return portfolioService.defaultReport(clientId);
    }

    /** Отметить, с какой даты по бумаге не поступают выплаты (повторный вызов заменяет дату). */
    @PutMapping("/defaults/{secId}")
    public ResponseEntity<Void> markDefault(@RequestHeader(CLIENT) String clientId,
                                            @PathVariable String secId,
                                            @Valid @RequestBody DefaultMarkRequest req) {
        bondDefaultService.mark(clientId, secId, req);
        return ResponseEntity.noContent().build();
    }

    /** Снять отметку: бумага снова считается по графику MOEX (или по признаку дефолта биржи). */
    @DeleteMapping("/defaults/{secId}")
    public ResponseEntity<Void> unmarkDefault(@RequestHeader(CLIENT) String clientId,
                                              @PathVariable String secId) {
        bondDefaultService.unmark(clientId, secId);
        return ResponseEntity.noContent().build();
    }
}
