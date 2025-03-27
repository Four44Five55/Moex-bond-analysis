package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            logger.info("Запуск приложения для анализа облигаций MOEX с учетом надежности эмитентов");

            // Инициализация компонентов
            MoexApiClient apiClient = new MoexApiClient();
            BondAnalyzer analyzer = new BondAnalyzer();
            ExcelWriter writer = new ExcelWriter();

            // 1. Загрузка данных
            logger.info("Загрузка данных с MOEX...");
            List<Bond> allBonds = apiClient.fetchBonds();
            logger.info("Получено {} облигаций, включая данные о надежности эмитентов", allBonds.size());

            // 2. Настройка критериев фильтрации
            FilterCriteria criteria = new FilterCriteria();
            criteria.setMinYield(7.0);               // Минимальная доходность 7%
            criteria.setMaxDuration(5.0);            // Максимальная дюрация 5 лет
            criteria.setMinReliabilityScore(7);      // Минимальная надежность (BBB- и выше)
            criteria.setOnlyInvestmentGrade(true);   // Только инвестиционные облигации
            criteria.setLimit(50);                   // Ограничить топ-50

            // 3. Фильтрация облигаций
            logger.info("Применение фильтров...");
            List<Bond> filteredBonds = analyzer.filterBonds(allBonds, criteria);

            if (filteredBonds.isEmpty()) {
                logger.warn("Не найдено облигаций, соответствующих критериям");
                logger.info("Максимальная доходность в данных: {}%",
                        allBonds.stream()
                                .mapToDouble(Bond::getYieldToMaturity)
                                .max()
                                .orElse(0));
                return;
            }

            logger.info("Найдено {} облигаций после фильтрации", filteredBonds.size());

            // 4. Дополнительный анализ
            double avgYield = analyzer.calculateAverageYield(filteredBonds);
            double avgReliability = filteredBonds.stream()
                    .mapToInt(Bond::getReliabilityScore)
                    .average()
                    .orElse(0);

            logger.info("Средняя доходность отобранных облигаций: {:.2f}%", avgYield);
            logger.info("Средний балл надежности: {:.1f}/10", avgReliability);

            // 5. Экспорт в Excel
            String outputPath = "output/moex_bonds_analyzed.xlsx";
            logger.info("Сохранение результатов в {}...", outputPath);
            writer.writeToExcel(filteredBonds, outputPath);

            logger.info("Анализ завершен. Результаты сохранены в {}", outputPath);

        } catch (Exception e) {
            logger.error("Критическая ошибка при выполнении анализа", e);
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}