-- Внешние движения денег по счёту: завёл деньги на брокерский счёт / вывел их обратно.
--
-- Свободный остаток намеренно НЕ хранится полем: он производная от истории
-- (пополнения − выводы − покупки − комиссии + продажи + полученные выплаты). Поле-остаток
-- пришлось бы синхронизировать с каждой сделкой и выплатой, и оно бы разъезжалось;
-- к тому же только история движений позволяет считать честную доходность на внесённые деньги.
CREATE TABLE cash_movement (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT       NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    direction  VARCHAR(8)   NOT NULL,                  -- IN | OUT
    moved_on   DATE         NOT NULL,
    amount     DOUBLE PRECISION NOT NULL,              -- всегда положительная, знак задаёт direction
    note       VARCHAR(512),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_cash_account ON cash_movement (account_id);
