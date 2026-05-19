-- TIMESTAMPTZ(UTC) → TIMESTAMP(KST) 변환
-- 기존 데이터: UTC 값을 Asia/Seoul(+09:00) 로컬 시각으로 변환

ALTER TABLE proposals
  ALTER COLUMN created_at TYPE TIMESTAMP USING (created_at AT TIME ZONE 'Asia/Seoul'),
  ALTER COLUMN updated_at TYPE TIMESTAMP USING (updated_at AT TIME ZONE 'Asia/Seoul');
ALTER TABLE proposals
  ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
  ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE claims
  ALTER COLUMN created_at TYPE TIMESTAMP USING (created_at AT TIME ZONE 'Asia/Seoul'),
  ALTER COLUMN updated_at TYPE TIMESTAMP USING (updated_at AT TIME ZONE 'Asia/Seoul');
ALTER TABLE claims
  ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
  ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE policies
  ALTER COLUMN created_at TYPE TIMESTAMP USING (created_at AT TIME ZONE 'Asia/Seoul'),
  ALTER COLUMN updated_at TYPE TIMESTAMP USING (updated_at AT TIME ZONE 'Asia/Seoul');
ALTER TABLE policies
  ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
  ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'Asia/Seoul');
