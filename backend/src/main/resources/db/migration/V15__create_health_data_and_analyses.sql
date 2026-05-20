CREATE TABLE health_data (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id   UUID         NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  source        VARCHAR(20)  NOT NULL,
  scenario      VARCHAR(20)  NOT NULL,
  payload       TEXT         NOT NULL,
  collected_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  collected_by  VARCHAR(50)  NOT NULL
);

CREATE INDEX idx_health_data_customer_id_collected_at
  ON health_data (customer_id, collected_at DESC);

CREATE TABLE health_analyses (
  id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id                 UUID         NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
  health_data_id              UUID         NOT NULL REFERENCES health_data(id),
  risk_grade                  VARCHAR(10)  NOT NULL,
  has_disease                 BOOLEAN      NOT NULL,
  diseases                    TEXT         NOT NULL,
  underwriting_recommendation VARCHAR(20)  NOT NULL,
  summary                     TEXT         NOT NULL,
  analyzed_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
  analyzed_by                 VARCHAR(50)  NOT NULL
);

CREATE INDEX idx_health_analyses_analyzed_by_analyzed_at
  ON health_analyses (analyzed_by, analyzed_at DESC);
