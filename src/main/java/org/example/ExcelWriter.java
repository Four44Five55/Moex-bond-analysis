package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ExcelWriter {
    // Цвета для разных уровней надежности (0-10)
    private static final short[] RELIABILITY_COLORS = {
            IndexedColors.RED.getIndex(),        // 0
            IndexedColors.RED.getIndex(),        // 1
            IndexedColors.RED.getIndex(),        // 2
            IndexedColors.RED.getIndex(),        // 3
            IndexedColors.RED.getIndex(),        // 4
            IndexedColors.ORANGE.getIndex(),     // 5
            IndexedColors.ORANGE.getIndex(),     // 6
            IndexedColors.YELLOW.getIndex(),     // 7
            IndexedColors.LIGHT_GREEN.getIndex(),// 8
            IndexedColors.GREEN.getIndex(),      // 9
            IndexedColors.BRIGHT_GREEN.getIndex()// 10
    };

    public void writeToExcel(List<Bond> bonds, String filePath) throws Exception {
        Path path = Paths.get(filePath);
        java.nio.file.Files.createDirectories(path.getParent());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Облигации");

            // Создание стилей
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle[] reliabilityStyles = createReliabilityStyles(workbook);

            // Заголовки
            String[] headers = {
                    "Код", "Наименование", "Квалификация", "Цена (%)",
                    "Ставка купона", "Объем сделок", "Доходность",
                    "Дюрация", "Выплаты в год", "Досрочное погашение",
                    "Дата выпуска", "Дата погашения", "Рейтинг", "Надежность"
            };

            // Создание строки заголовков
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Заполнение данных
            for (int i = 0; i < bonds.size(); i++) {
                Bond bond = bonds.get(i);
                Row row = sheet.createRow(i + 1);

                // Основные данные
                row.createCell(0).setCellValue(bond.getSecId());
                row.createCell(1).setCellValue(bond.getFullName());
                row.createCell(2).setCellValue(bond.getQualification());
                row.createCell(3).setCellValue(bond.getPricePercent());
                row.createCell(4).setCellValue(bond.getCouponRate());
                row.createCell(5).setCellValue(bond.getTradeVolume());
                row.createCell(6).setCellValue(bond.getYieldToMaturity());
                row.createCell(7).setCellValue(bond.getDuration());
                row.createCell(8).setCellValue(bond.getCouponFrequency());
                row.createCell(9).setCellValue(bond.isCallable() ? "Да" : "Нет");

                // Даты
                Cell issueDateCell = row.createCell(10);
                issueDateCell.setCellValue(bond.getIssueDate());
                issueDateCell.setCellStyle(dateStyle);

                Cell maturityDateCell = row.createCell(11);
                maturityDateCell.setCellValue(bond.getMaturityDate());
                maturityDateCell.setCellStyle(dateStyle);

                // Данные о надежности
                row.createCell(12).setCellValue(bond.getIssuerRating());

                Cell reliabilityCell = row.createCell(13);
                reliabilityCell.setCellValue(bond.getReliabilityScore());

                // Безопасное применение стиля
                int score = Math.max(0, Math.min(bond.getReliabilityScore(), 10));
                reliabilityCell.setCellStyle(reliabilityStyles[score]);
            }

            // Автоподбор ширины колонок
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Сохранение файла
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));
        return style;
    }

    private CellStyle[] createReliabilityStyles(Workbook workbook) {
        CellStyle[] styles = new CellStyle[11]; // Индексы 0-10
        for (int i = 0; i <= 10; i++) {
            styles[i] = workbook.createCellStyle();
            styles[i].setFillForegroundColor(RELIABILITY_COLORS[i]);
            styles[i].setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return styles;
    }
}