-- НКД и дата оферты для облигаций (фича 1B: детализация карточки)
ALTER TABLE bond ADD COLUMN accrued_interest DOUBLE PRECISION;
ALTER TABLE bond ADD COLUMN offer_date       DATE;
