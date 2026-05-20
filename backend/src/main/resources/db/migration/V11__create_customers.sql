CREATE TABLE customers (
  id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id    VARCHAR(50)   NOT NULL,
  name        VARCHAR(500)  NOT NULL,
  phone       VARCHAR(500)  NOT NULL,
  birth_date  DATE,
  gender      VARCHAR(10),
  email       VARCHAR(500),
  address     VARCHAR(1000),
  memo        TEXT,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_customers_agent_id ON customers (agent_id);
