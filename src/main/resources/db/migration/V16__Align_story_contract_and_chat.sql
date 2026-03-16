ALTER TABLE loans ADD COLUMN IF NOT EXISTS agreement_finalized_at TIMESTAMP;

ALTER TABLE negotiation_messages ALTER COLUMN sender_id DROP NOT NULL;
