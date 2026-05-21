ALTER TABLE policies ADD COLUMN customer_id UUID NULL;
ALTER TABLE policies ADD CONSTRAINT fk_policies_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id);
CREATE INDEX idx_policies_customer_id ON policies(customer_id);

UPDATE policies p
SET customer_id = c.id
FROM customers c
WHERE p.agent_id = c.agent_id
  AND p.customer_name = c.name
  AND p.customer_id IS NULL;
