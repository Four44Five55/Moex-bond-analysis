-- V3 добавила accrued_interest как nullable и без DEFAULT, поэтому у всех облигаций,
-- загруженных до неё, там остался NULL. Поле в сущности — примитивный double, и Hibernate
-- падал на чтении такой строки ("Null value was assigned to a property of primitive type"),
-- то есть весь список облигаций отдавал 500 до первого ре-синка.
--
-- НКД у облигации определён всегда (0 — сразу после выплаты купона), так что NOT NULL DEFAULT 0
-- не только чинит существующие строки, но и не даёт багу повториться: колонка больше не
-- может принять NULL, даже если парсер однажды перестанет её заполнять.
UPDATE bond SET accrued_interest = 0 WHERE accrued_interest IS NULL;

ALTER TABLE bond ALTER COLUMN accrued_interest SET DEFAULT 0;
ALTER TABLE bond ALTER COLUMN accrued_interest SET NOT NULL;
