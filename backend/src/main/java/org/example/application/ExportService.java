package org.example.application;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.domain.model.InstrumentType;
import org.example.web.dto.InstrumentDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Function;

/**
 * Экспорт инструментов в Excel (.xlsx). Набор колонок зависит от типа инструмента.
 */
@Service
public class ExportService {

    private record Column(String title, Function<InstrumentDto, Object> value) {
    }

    private static final List<Column> BOND_COLUMNS = List.of(
            new Column("Код", InstrumentDto::secId),
            new Column("ISIN", InstrumentDto::isin),
            new Column("Название", InstrumentDto::shortName),
            new Column("Валюта", InstrumentDto::currency),
            new Column("Цена, %", InstrumentDto::lastPrice),
            new Column("Купон, %", InstrumentDto::couponRate),
            new Column("Доходность, %", InstrumentDto::yieldToMaturity),
            new Column("Дюрация, лет", InstrumentDto::duration),
            new Column("Надёжность", InstrumentDto::reliabilityScore),
            new Column("Рейтинг", InstrumentDto::issuerRating),
            new Column("Инв. уровень", d -> Boolean.TRUE.equals(d.investmentGrade()) ? "Да" : "Нет"),
            new Column("Погашение", InstrumentDto::maturityDate)
    );

    private static final List<Column> SHARE_COLUMNS = List.of(
            new Column("Код", InstrumentDto::secId),
            new Column("ISIN", InstrumentDto::isin),
            new Column("Название", InstrumentDto::shortName),
            new Column("Валюта", InstrumentDto::currency),
            new Column("Цена", InstrumentDto::lastPrice),
            new Column("Изм. за день, %", InstrumentDto::dayChangePct),
            new Column("Объём", InstrumentDto::tradeVolume),
            new Column("Капитализация", InstrumentDto::capitalization),
            new Column("Лот", InstrumentDto::lotSize),
            new Column("Листинг", InstrumentDto::listLevel)
    );

    public byte[] toXlsx(List<InstrumentDto> rows, InstrumentType type) {
        List<Column> columns = type == InstrumentType.SHARE ? SHARE_COLUMNS : BOND_COLUMNS;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(type == InstrumentType.SHARE ? "Акции" : "Облигации");

            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).title());
                cell.setCellStyle(headerStyle);
            }

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                InstrumentDto dto = rows.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    writeCell(row.createCell(c), columns.get(c).value().apply(dto));
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать Excel: " + e.getMessage(), e);
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}
