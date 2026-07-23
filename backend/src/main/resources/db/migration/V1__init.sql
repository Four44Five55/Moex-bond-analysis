-- Базовая таблица инструментов (JPA JOINED inheritance, дискриминатор instrument_type)
CREATE TABLE instrument (
    id               BIGSERIAL PRIMARY KEY,
    instrument_type  VARCHAR(16)  NOT NULL,          -- BOND | SHARE
    sec_id           VARCHAR(64)  NOT NULL UNIQUE,
    isin             VARCHAR(32),
    short_name       VARCHAR(255),
    full_name        VARCHAR(512),
    currency         VARCHAR(16),
    board            VARCHAR(32),
    market           VARCHAR(32),
    last_price       DOUBLE PRECISION,
    prev_price       DOUBLE PRECISION,
    day_change_pct   DOUBLE PRECISION,
    trade_volume     DOUBLE PRECISION,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_instrument_type      ON instrument (instrument_type);
CREATE INDEX idx_instrument_short     ON instrument (lower(short_name));

-- Детали облигаций (1:1 к instrument)
CREATE TABLE bond (
    id                 BIGINT PRIMARY KEY REFERENCES instrument (id) ON DELETE CASCADE,
    face_value         DOUBLE PRECISION,
    coupon_rate        DOUBLE PRECISION,
    ytm                DOUBLE PRECISION,
    duration           DOUBLE PRECISION,
    convexity          DOUBLE PRECISION,
    coupon_frequency   INT,
    maturity_date      DATE,
    next_coupon_date   DATE,
    issue_date         DATE,
    issuer_rating      VARCHAR(32),
    reliability_score  INT,
    investment_grade   BOOLEAN,
    callable           BOOLEAN,
    puttable           BOOLEAN,
    list_level         INT,
    suspicious_yield   BOOLEAN NOT NULL DEFAULT false
);

-- Детали акций (1:1 к instrument)
CREATE TABLE share (
    id              BIGINT PRIMARY KEY REFERENCES instrument (id) ON DELETE CASCADE,
    lot_size        INT,
    capitalization  DOUBLE PRECISION,
    sector          VARCHAR(128),
    list_level      INT,
    issue_size      DOUBLE PRECISION
);

-- Исторические свечи
CREATE TABLE candle (
    id             BIGSERIAL PRIMARY KEY,
    sec_id         VARCHAR(64)  NOT NULL,
    market         VARCHAR(32)  NOT NULL,
    interval_code  INT          NOT NULL,             -- код интервала MOEX (24 = день)
    ts             TIMESTAMPTZ  NOT NULL,             -- начало свечи
    open           DOUBLE PRECISION,
    high           DOUBLE PRECISION,
    low            DOUBLE PRECISION,
    close          DOUBLE PRECISION,
    volume         DOUBLE PRECISION,
    CONSTRAINT uq_candle UNIQUE (sec_id, interval_code, ts)
);

CREATE INDEX idx_candle_lookup ON candle (sec_id, interval_code, ts);

-- Анонимный список избранного (ключ — X-Client-Id)
CREATE TABLE watchlist_item (
    id         BIGSERIAL PRIMARY KEY,
    client_id  VARCHAR(64) NOT NULL,
    sec_id     VARCHAR(64) NOT NULL,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_watchlist UNIQUE (client_id, sec_id)
);

CREATE INDEX idx_watchlist_client ON watchlist_item (client_id);
