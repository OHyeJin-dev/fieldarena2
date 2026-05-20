ALTER TABLE claims ADD COLUMN customer_id UUID;
ALTER TABLE claims ADD CONSTRAINT fk_claims_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id);
CREATE INDEX idx_claims_customer_id ON claims (customer_id);
