-- Дефолты облигаций.
--
-- График выплат MOEX (bondization) — это график по проспекту, а не факт: у эмитента в дефолте
-- он продолжает показывать купоны и погашение, которых никто не получит. Портфель же считал
-- каждую прошедшую дату графика полученными деньгами, а будущие — прогнозом дохода.

-- Признаки MOEX. Ставятся синхронизацией (режим торгов TQRD — «Облигации Д», торги бумагами
-- в дефолте) и дополняются из описания бумаги (HASDEFAULT / HASTECHNICALDEFAULT).
-- Примитивные поля сущности ⟹ NOT NULL DEFAULT (см. V9).
ALTER TABLE bond
    ADD COLUMN has_default           BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN has_technical_default BOOLEAN NOT NULL DEFAULT FALSE;

-- Отметка клиента: «по этой бумаге выплаты не поступают начиная с даты».
-- MOEX не сообщает ни даты дефолта, ни того, какой именно платёж не прошёл, поэтому дату
-- задаёт сам инвестор. Свойство бумаги, а не счёта: один и тот же эмитент не платит
-- одинаково на всех счетах клиента.
CREATE TABLE bond_default_mark (
    id                  BIGSERIAL PRIMARY KEY,
    client_id           VARCHAR(64)  NOT NULL,
    sec_id              VARCHAR(64)  NOT NULL,
    payments_stopped_on DATE         NOT NULL,
    note                VARCHAR(512),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_default_mark UNIQUE (client_id, sec_id)
);

-- Деньги, всё же полученные по бумаге после дефолта: реструктуризация, выплаты в банкротстве,
-- частичное погашение. Привязаны к счёту — деньги приходят на конкретный счёт и меняют его остаток.
CREATE TABLE default_recovery (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    sec_id      VARCHAR(64)    NOT NULL,
    received_on DATE           NOT NULL,
    amount      NUMERIC(19, 2) NOT NULL,
    note        VARCHAR(512),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_recovery_account ON default_recovery (account_id);
