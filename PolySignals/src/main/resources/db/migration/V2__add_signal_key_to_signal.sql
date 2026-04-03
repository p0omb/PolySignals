ALTER TABLE signal
    ADD COLUMN signal_key varchar(255);

-- Backfill исторических строк уникальными значениями, чтобы миграция прошла без конфликтов.
UPDATE signal
SET signal_key = concat(market_id, ':', outcome, ':', id)
WHERE signal_key IS NULL;

ALTER TABLE signal
    ALTER COLUMN signal_key SET NOT NULL;

ALTER TABLE signal
    ADD CONSTRAINT uk_signal_signal_key UNIQUE (signal_key);

