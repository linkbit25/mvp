ALTER TABLE loans ADD COLUMN IF NOT EXISTS collateral_released_at TIMESTAMP;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS collateral_released_btc DECIMAL(19, 8);

CREATE TABLE IF NOT EXISTS collateral_releases (
    id UUID PRIMARY KEY DEFAULT random_uuid(),
    loan_id UUID NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    released_btc DECIMAL(19, 8) NOT NULL,
    executed_by UUID REFERENCES users(id),
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
