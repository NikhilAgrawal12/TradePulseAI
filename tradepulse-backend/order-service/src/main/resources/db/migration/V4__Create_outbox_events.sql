CREATE TABLE IF NOT EXISTS outbox_events (
    outbox_id    VARCHAR(36)   NOT NULL PRIMARY KEY,
    order_id     VARCHAR(36)   NOT NULL,
    event_type   VARCHAR(64)   NOT NULL,
    topic        VARCHAR(256)  NOT NULL,
    payload      TEXT          NOT NULL,
    partition_key VARCHAR(256),
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events (status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_events (created_at);

