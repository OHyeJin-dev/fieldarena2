-- V16__add_customer_id_to_proposals.sql
ALTER TABLE proposals ADD COLUMN customer_id UUID NULL;
ALTER TABLE proposals ADD CONSTRAINT fk_proposals_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL;
CREATE INDEX idx_proposals_customer_id ON proposals(customer_id);

UPDATE proposals p
SET customer_id = c.id
FROM customers c
WHERE p.agent_id = c.agent_id
  AND p.customer_name = c.name
  AND p.customer_id IS NULL;
