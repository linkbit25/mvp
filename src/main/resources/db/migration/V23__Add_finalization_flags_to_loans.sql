ALTER TABLE loans ADD COLUMN lender_finalized BOOLEAN DEFAULT FALSE;
ALTER TABLE loans ADD COLUMN borrower_finalized BOOLEAN DEFAULT FALSE;

-- Retroactively set flags for loans already past negotiation
UPDATE loans SET lender_finalized = TRUE, borrower_finalized = TRUE WHERE status <> 'NEGOTIATING';
