-- Приведение схемы к инварианту «примитивное поле сущности ⟹ колонка NOT NULL».
--
-- Зачем: Hibernate, прочитав NULL в поле типа double/int/boolean, бросает
-- «Null value was assigned to a property of primitive type» и отдаёт 500 на ЛЮБОЙ строке
-- таблицы, а не только на испорченной. Ровно так однажды слегла вся выдача облигаций:
-- V3 добавила bond.accrued_interest как nullable без DEFAULT, и всем уже загруженным
-- выпускам туда встал NULL (починено в V4). Здесь закрываются остальные такие колонки,
-- пока они не выстрелили: до сих пор их спасало только то, что парсер всегда что-то пишет.
--
-- Мина взводится не при вставке, а при ALTER TABLE ADD COLUMN: значение появляется у новых
-- строк, а старым достаётся NULL. Поэтому ни модульные тесты, ни свежая база из
-- docker compose это не ловят — схема там создаётся с нуля и старых строк не имеет.
-- Инвариант теперь проверяется тестом PrimitiveColumnsAreNotNullIT.
--
-- Обратная половина решения — в коде: bond.ytm, duration, convexity, coupon_frequency,
-- reliability_score и list_level ОСТАЮТСЯ nullable, а поля под ними стали обёртками
-- (Double/Integer). У выпуска без сделок доходности и дюрации действительно нет, и ноль
-- вместо них не нейтрален: он проходит фильтр «дюрация не больше N» и встаёт в сортировку
-- по доходности как настоящее значение. NULL такие выпуски из сравнений исключает.

-- Номинал и купонная ставка у облигации есть всегда: выпуск без номинала парсер отбраковывает.
UPDATE bond SET face_value  = 0 WHERE face_value  IS NULL;
UPDATE bond SET coupon_rate = 0 WHERE coupon_rate IS NULL;

ALTER TABLE bond
    ALTER COLUMN face_value  SET DEFAULT 0,
    ALTER COLUMN face_value  SET NOT NULL,
    ALTER COLUMN coupon_rate SET DEFAULT 0,
    ALTER COLUMN coupon_rate SET NOT NULL;

-- Признаки: «неизвестно» и «нет» здесь одно и то же — оферты либо объявлены, либо нет.
UPDATE bond SET investment_grade = FALSE WHERE investment_grade IS NULL;
UPDATE bond SET callable         = FALSE WHERE callable         IS NULL;
UPDATE bond SET puttable         = FALSE WHERE puttable         IS NULL;

ALTER TABLE bond
    ALTER COLUMN investment_grade SET DEFAULT FALSE,
    ALTER COLUMN investment_grade SET NOT NULL,
    ALTER COLUMN callable         SET DEFAULT FALSE,
    ALTER COLUMN callable         SET NOT NULL,
    ALTER COLUMN puttable         SET DEFAULT FALSE,
    ALTER COLUMN puttable         SET NOT NULL;

-- Свеча без цен смысла не имеет: если цены нет, не должно быть и строки.
UPDATE candle SET open = 0 WHERE open IS NULL;
UPDATE candle SET high = 0 WHERE high IS NULL;
UPDATE candle SET low = 0 WHERE low IS NULL;
UPDATE candle SET close = 0 WHERE close IS NULL;
UPDATE candle SET volume = 0 WHERE volume IS NULL;

ALTER TABLE candle
    ALTER COLUMN open   SET DEFAULT 0,
    ALTER COLUMN open   SET NOT NULL,
    ALTER COLUMN high   SET DEFAULT 0,
    ALTER COLUMN high   SET NOT NULL,
    ALTER COLUMN low    SET DEFAULT 0,
    ALTER COLUMN low    SET NOT NULL,
    ALTER COLUMN close  SET DEFAULT 0,
    ALTER COLUMN close  SET NOT NULL,
    ALTER COLUMN volume SET DEFAULT 0,
    ALTER COLUMN volume SET NOT NULL;
