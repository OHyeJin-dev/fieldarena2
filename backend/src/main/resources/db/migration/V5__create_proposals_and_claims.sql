CREATE TABLE proposals (
  id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id       VARCHAR(50)   NOT NULL,
  customer_name  VARCHAR(50)   NOT NULL,
  product_name   VARCHAR(100)  NOT NULL,
  insurer_name   VARCHAR(50)   NOT NULL,
  monthly_premium NUMERIC(12,2),
  status         VARCHAR(20)   NOT NULL,
  proposed_date  DATE          NOT NULL,
  created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_proposals_agent_id    ON proposals (agent_id);
CREATE INDEX idx_proposals_status      ON proposals (status);
CREATE INDEX idx_proposals_proposed_date ON proposals (proposed_date DESC);

CREATE TABLE claims (
  id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id       VARCHAR(50)   NOT NULL,
  policy_number  VARCHAR(20)   NOT NULL,
  customer_name  VARCHAR(50)   NOT NULL,
  insurer_name   VARCHAR(50)   NOT NULL,
  claim_type     VARCHAR(50)   NOT NULL,
  claim_amount   NUMERIC(12,2),
  status         VARCHAR(20)   NOT NULL,
  claim_date     DATE          NOT NULL,
  created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_claims_agent_id   ON claims (agent_id);
CREATE INDEX idx_claims_status     ON claims (status);
CREATE INDEX idx_claims_claim_date ON claims (claim_date DESC);
