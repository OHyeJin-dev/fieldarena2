CREATE TABLE policies (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_number   VARCHAR(20)     NOT NULL UNIQUE,
  agent_id        VARCHAR(50)     NOT NULL,
  customer_name   VARCHAR(50)     NOT NULL,
  product_name    VARCHAR(100)    NOT NULL,
  insurer_name    VARCHAR(50)     NOT NULL,
  status          VARCHAR(20)     NOT NULL,
  contract_date   DATE            NOT NULL,
  monthly_premium NUMERIC(12, 2),
  created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_policies_agent_id      ON policies (agent_id);
CREATE INDEX idx_policies_contract_date ON policies (contract_date DESC);
CREATE INDEX idx_policies_status        ON policies (status);
