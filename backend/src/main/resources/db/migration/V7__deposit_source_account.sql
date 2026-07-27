-- Счёт, с которого сняты деньги на вклад. NULL — вклад открыт деньгами со стороны.
-- Отличать эти два случая нужно для расчёта внесённого капитала: перевод с брокерского счёта
-- новым капиталом не является, иначе одни и те же деньги считаются внесёнными дважды.
ALTER TABLE deposit ADD COLUMN source_account_id BIGINT REFERENCES account (id) ON DELETE SET NULL;

CREATE INDEX idx_deposit_source_account ON deposit (source_account_id);
