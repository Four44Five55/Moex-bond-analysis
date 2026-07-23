package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Обёртка над блоком ответа MOEX ISS вида {@code {"columns": [...], "data": [[...], ...]}}.
 * Скрывает индексацию по именам столбцов и безопасное приведение типов.
 */
public class MoexTable implements Iterable<MoexTable.Row> {
    private static final Logger logger = LoggerFactory.getLogger(MoexTable.class);

    private final Map<String, Integer> columnIndex = new HashMap<>();
    private final JsonNode data;

    private MoexTable(JsonNode columns, JsonNode data) {
        for (int i = 0; i < columns.size(); i++) {
            columnIndex.put(columns.get(i).asText(), i);
        }
        this.data = data;
    }

    /** Возвращает таблицу для именованного блока, либо null, если блок/структура отсутствуют. */
    public static MoexTable of(JsonNode root, String block) {
        if (root == null || !root.has(block)) {
            return null;
        }
        JsonNode node = root.get(block);
        if (!node.has("columns") || !node.has("data")) {
            return null;
        }
        return new MoexTable(node.get("columns"), node.get("data"));
    }

    public boolean has(String column) {
        return columnIndex.containsKey(column);
    }

    @Override
    public Iterator<Row> iterator() {
        Iterator<JsonNode> it = data.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Row next() {
                return new Row(it.next());
            }
        };
    }

    /** Одна строка данных с типобезопасными геттерами по имени столбца. */
    public class Row {
        private final JsonNode node;

        Row(JsonNode node) {
            this.node = node;
        }

        public String getString(String column) {
            Integer i = columnIndex.get(column);
            return i == null ? "" : node.get(i).asText();
        }

        public double getDouble(String column) {
            Integer i = columnIndex.get(column);
            return i == null ? 0.0 : node.get(i).asDouble(0.0);
        }

        public int getInt(String column) {
            Integer i = columnIndex.get(column);
            return i == null ? 0 : node.get(i).asInt(0);
        }

        public boolean hasNonEmpty(String column) {
            Integer i = columnIndex.get(column);
            if (i == null) {
                return false;
            }
            String v = node.get(i).asText();
            return v != null && !v.isEmpty();
        }

        public LocalDate getDate(String column) {
            Integer i = columnIndex.get(column);
            if (i == null) {
                return null;
            }
            String v = node.get(i).asText();
            if (v == null || v.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(v);
            } catch (DateTimeParseException e) {
                logger.debug("Не удалось разобрать дату {}='{}'", column, v);
                return null;
            }
        }
    }
}
