-- Инвестиционные счета (анонимные, по client_id)
CREATE TABLE account (
    id                     BIGSERIAL PRIMARY KEY,
    client_id              VARCHAR(64)  NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    type                   VARCHAR(16)  NOT NULL,          -- BROKERAGE | IIS
    iis_type               VARCHAR(4),                     -- A | B (только для IIS)
    default_commission_pct DOUBLE PRECISION NOT NULL DEFAULT 0,
    base_tax_rate          DOUBLE PRECISION NOT NULL DEFAULT 13,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_client ON account (client_id);

-- Сделки по счёту
CREATE TABLE txn (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT       NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    sec_id      VARCHAR(64)  NOT NULL,
    side        VARCHAR(8)   NOT NULL,                     -- BUY | SELL
    trade_date  DATE         NOT NULL,
    quantity    DOUBLE PRECISION NOT NULL,
    price       DOUBLE PRECISION NOT NULL,                 -- ₽/шт (акции) или % номинала (облигации)
    aci         DOUBLE PRECISION NOT NULL DEFAULT 0,       -- НКД на облигацию
    commission  DOUBLE PRECISION NOT NULL DEFAULT 0,
    note        VARCHAR(512),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_txn_account     ON txn (account_id);
CREATE INDEX idx_txn_account_sec ON txn (account_id, sec_id);
