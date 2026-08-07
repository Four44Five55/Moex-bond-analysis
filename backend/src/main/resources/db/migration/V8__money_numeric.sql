-- Денежные колонки учётного контура переводятся из DOUBLE PRECISION в NUMERIC.
--
-- Зачем: суммы по счёту складываются из сотен сделок, комиссий и выплат, и двоичная дробь
-- не умеет точно представить даже 0.1 ₽. По отдельной сделке расхождение незаметно, но налог
-- и внесённый капитал считаются свёрткой всей истории, и там оно всплывает копейками, которые
-- не сходятся с отчётом брокера. NUMERIC хранит десятичное значение точно.
--
-- Что НЕ переводится (сознательно): котировки (instrument.last_price, candle.*), YTM, дюрация,
-- выпуклость. Это либо приблизительные по своей природе величины, либо результат итеративного
-- решения уравнения, где десятичная точность смысла не имеет. Подробнее — в javadoc Money.
--
-- Масштабы: 6 знаков для величин «на одну бумагу» (цена в % номинала, НКД), 2 — для итоговых
-- сумм, 4 — для ставок в процентах. Округление до целевого масштаба обязательно: без него
-- ALTER падает на значениях, у которых после конвертации дробная часть длиннее колонки.
--
-- Приведение float8 -> numeric берёт кратчайшее десятичное представление, которое
-- round-trip'ится обратно в тот же double (так float8 выводится начиная с PostgreSQL 12),
-- поэтому записанные когда-то 0.1 остаются 0.1, а не превращаются в 0.100000000000000005.

-- Ставки по счёту: 14.2500 %
ALTER TABLE account
    ALTER COLUMN default_commission_pct TYPE NUMERIC(9, 4) USING ROUND(default_commission_pct::NUMERIC, 4),
    ALTER COLUMN base_tax_rate          TYPE NUMERIC(9, 4) USING ROUND(base_tax_rate::NUMERIC, 4);

-- Сделки. quantity тоже NUMERIC, а не BIGINT: дробных бумаг на MOEX нет, но переход на целое
-- сломал бы уже заведённые сделки, где количество когда-то ввели дробным.
ALTER TABLE txn
    ALTER COLUMN quantity   TYPE NUMERIC(19, 6) USING ROUND(quantity::NUMERIC, 6),
    ALTER COLUMN price      TYPE NUMERIC(19, 6) USING ROUND(price::NUMERIC, 6),
    ALTER COLUMN aci        TYPE NUMERIC(19, 6) USING ROUND(aci::NUMERIC, 6),
    ALTER COLUMN commission TYPE NUMERIC(19, 2) USING ROUND(commission::NUMERIC, 2);

-- Движения денег: всегда итоговая сумма в ₽, дробить мельче копейки нечего.
ALTER TABLE cash_movement
    ALTER COLUMN amount TYPE NUMERIC(19, 2) USING ROUND(amount::NUMERIC, 2);

ALTER TABLE deposit
    ALTER COLUMN principal TYPE NUMERIC(19, 2) USING ROUND(principal::NUMERIC, 2),
    ALTER COLUMN rate_pct  TYPE NUMERIC(9, 4)  USING ROUND(rate_pct::NUMERIC, 4);
