-- Банковские вклады. Привязаны к клиенту, а не к брокерскому счёту: у вклада нет сделок,
-- бумаг и котировок, и натягивать его на account/txn пришлось бы ценой пустых полей в обеих
-- сущностях. В сводке по капиталу вклады идут отдельной строкой рядом со счетами.
CREATE TABLE deposit (
    id            BIGSERIAL PRIMARY KEY,
    client_id     VARCHAR(64)  NOT NULL,
    name          VARCHAR(255) NOT NULL,          -- банк / название вклада
    principal     DOUBLE PRECISION NOT NULL,      -- сумма вклада, ₽
    rate_pct      DOUBLE PRECISION NOT NULL,      -- годовая ставка, %
    opened_on     DATE         NOT NULL,
    matures_on    DATE         NOT NULL,
    capitalized   BOOLEAN      NOT NULL DEFAULT FALSE,  -- проценты причисляются к вкладу
    payout_period VARCHAR(16)  NOT NULL,          -- MONTHLY | QUARTERLY | SEMIANNUAL | ANNUAL | AT_MATURITY
    note          VARCHAR(512),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_deposit_client ON deposit (client_id);
