CREATE TABLE users (
  id         VARCHAR(50)  PRIMARY KEY,
  password   VARCHAR(255) NOT NULL,
  name       VARCHAR(500) NOT NULL,
  phone      VARCHAR(500) NOT NULL,
  ga_name    VARCHAR(100) NOT NULL,
  email      VARCHAR(500) NOT NULL,
  email_hash VARCHAR(64)  NOT NULL UNIQUE,
  role       VARCHAR(20),
  status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_status ON users (status);
