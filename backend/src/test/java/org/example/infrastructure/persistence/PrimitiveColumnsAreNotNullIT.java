package org.example.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Инвариант схемы: <b>колонка под примитивным полем сущности обязана быть NOT NULL</b>.
 *
 * <h2>Что именно ловится</h2>
 * Hibernate читает {@code NULL} в поле типа {@code double}/{@code int}/{@code boolean} и бросает
 * «Null value was assigned to a property of primitive type» — то есть отдаёт 500 на <i>любой</i>
 * строке таблицы, а не только на испорченной. Так однажды слегла вся выдача облигаций: миграция
 * {@code V3} добавила {@code bond.accrued_interest} как nullable без DEFAULT, и всем 3099 уже
 * загруженным выпускам туда встал {@code NULL} (починено в {@code V4}).
 *
 * <h2>Почему обычные проверки это пропускают</h2>
 * Мина взводится не при вставке, а в момент {@code ALTER TABLE ADD COLUMN}: значение появляется
 * только у новых строк, а старым достаётся {@code NULL}. Поэтому и модульные тесты, и свежая база
 * из {@code docker compose up} остаются зелёными — схема там создаётся с нуля и старых строк
 * не имеет. Ломается ровно та инсталляция, где приложение уже работало.
 * <p>
 * {@code ddl-auto: validate} здесь тоже не помощник: Hibernate сверяет существование колонок
 * и их типы, но <b>не nullability</b>. Инструмент выглядит подходящим и создаёт ложное
 * чувство защиты — отсюда этот тест.
 *
 * <h2>Проверяется одно направление</h2>
 * Только «примитив ⟹ NOT NULL». Обратное («обёртка ⟹ nullable») не утверждается: обёртка
 * над NOT NULL-колонкой законна и означает «значение обязано быть, но проставляет его приложение»
 * — так живут, например, {@code Instrument.updatedAt} и все строковые поля.
 *
 * <h2>Если тест покраснел</h2>
 * Решает один вопрос: <b>является ли «значения нет» осмысленным состоянием?</b>
 * Если да — поле должно стать обёрткой ({@code Double}/{@code Integer}), колонка остаётся
 * nullable. Если нет — миграция приводит колонку к {@code NOT NULL DEFAULT} с backfill
 * существующих строк, как это сделано в {@code V4}. Проставлять ноль там, где величина
 * действительно неизвестна, нельзя: такой ноль потом всплывёт в фильтрах и сортировках
 * как настоящее значение.
 */
@SpringBootTest(properties = {
        "moex.sync.enabled=false",
        "spring.cache.type=none"
})
@Testcontainers
class PrimitiveColumnsAreNotNullIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyPrimitiveEntityFieldIsBackedByANotNullColumn() {
        Map<String, Boolean> nullableByColumn = loadSchemaNullability();
        // Множества, а не списки: у JOINED-наследования поля Instrument попадают в обход
        // дважды — при разборе самого Instrument и при подъёме по иерархии от Bond и Share
        Set<String> violations = new LinkedHashSet<>();
        Set<String> unmapped = new LinkedHashSet<>();

        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            for (Class<?> type = entity.getJavaType();
                 type != null && type != Object.class;
                 type = type.getSuperclass()) {

                String table = tableOf(type);
                if (table == null) {
                    continue;   // маппированный суперкласс без своей таблицы
                }
                for (Field field : type.getDeclaredFields()) {
                    if (!isPersistentPrimitive(field)) {
                        continue;
                    }
                    String key = key(table, columnOf(field));
                    Boolean nullable = nullableByColumn.get(key);
                    if (nullable == null) {
                        // Имя колонки не совпало со схемой — молча пропустить нельзя,
                        // иначе тест будет зелёным именно там, где ничего не проверил
                        unmapped.add("%s.%s → колонка %s не найдена"
                                .formatted(type.getSimpleName(), field.getName(), key));
                    } else if (nullable) {
                        violations.add("%s.%s (%s) — колонка %s допускает NULL"
                                .formatted(type.getSimpleName(), field.getName(),
                                        field.getType().getSimpleName(), key));
                    }
                }
            }
        }

        assertThat(unmapped)
                .as("колонки, которые тест не смог сопоставить со схемой — проверка их не покрыла")
                .isEmpty();
        assertThat(violations)
                .as("примитивное поле на nullable-колонке роняет чтение всей таблицы, "
                        + "см. описание класса")
                .isEmpty();
    }

    /** Nullability всех колонок текущей схемы: {@code таблица.колонка → допускает ли NULL}. */
    private Map<String, Boolean> loadSchemaNullability() {
        Map<String, Boolean> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT table_name, column_name, is_nullable
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                """,
                rs -> {
                    result.put(key(rs.getString("table_name"), rs.getString("column_name")),
                            "YES".equals(rs.getString("is_nullable")));
                });
        return result;
    }

    private static boolean isPersistentPrimitive(Field field) {
        return field.getType().isPrimitive()
                && !Modifier.isStatic(field.getModifiers())
                && !Modifier.isTransient(field.getModifiers())
                && !field.isAnnotationPresent(Transient.class);
    }

    /** Таблица, объявленная на самом классе; {@code null} — если класс своей таблицы не имеет. */
    private static String tableOf(Class<?> type) {
        Table table = type.getDeclaredAnnotation(Table.class);
        return table != null && !table.name().isBlank() ? table.name() : null;
    }

    /**
     * Имя колонки: из {@code @Column}, иначе по стратегии Spring Boot — camelCase в snake_case.
     * Расхождение с реальной схемой не проглатывается, а падает как {@code unmapped}.
     */
    private static String columnOf(Field field) {
        Column column = field.getDeclaredAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }
        return camelToSnake(field.getName());
    }

    private static String camelToSnake(String name) {
        StringBuilder result = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }

    private static String key(String table, String column) {
        return (table + "." + column).toLowerCase(Locale.ROOT);
    }
}
