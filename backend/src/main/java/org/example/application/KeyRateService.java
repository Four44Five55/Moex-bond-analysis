package org.example.application;

import org.example.domain.port.KeyRateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Ключевая ставка для налога по вкладам, с запасным значением из конфигурации.
 * <p>
 * Недоступность ЦБ не должна ронять сводку по капиталу: без ставки нельзя посчитать только
 * вычет по вкладам, а не весь портфель. Поэтому сбой источника логируется и подменяется
 * настроенным значением — заведомо приблизительным, но лучше, чем 500 на всю страницу.
 */
@Service
public class KeyRateService {

    private static final Logger log = LoggerFactory.getLogger(KeyRateService.class);

    private final KeyRateProvider keyRateProvider;
    private final double fallbackPct;

    public KeyRateService(KeyRateProvider keyRateProvider,
                          @Value("${app.tax.key-rate-fallback-pct}") double fallbackPct) {
        this.keyRateProvider = keyRateProvider;
        this.fallbackPct = fallbackPct;
    }

    public double maxForYear(int year) {
        try {
            return keyRateProvider.maxKeyRateForYear(year);
        } catch (RuntimeException e) {
            log.warn("Ключевая ставка ЦБ за {} недоступна, берём запасное значение {}%: {}",
                    year, fallbackPct, e.getMessage());
            return fallbackPct;
        }
    }
}
