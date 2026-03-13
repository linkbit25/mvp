-- Update loan_status enum to append MARGIN_CALL and LIQUIDATION_ELIGIBLE
ALTER TABLE loans ALTER COLUMN status VARCHAR(255) CHECK (status IN ('NEGOTIATING', 'AWAITING_SIGNATURES', 'AWAITING_FEE', 'AWAITING_COLLATERAL', 'COLLATERAL_LOCKED', 'AGREED', 'ACTIVE', 'DEFAULTED', 'CLOSED', 'CANCELLED', 'DISPUTE_OPEN', 'LIQUIDATED', 'REPAID', 'MARGIN_CALL', 'LIQUIDATION_ELIGIBLE'));

-- Add LTV monitoring fields to loans table
ALTER TABLE loans
    ADD COLUMN collateral_btc_amount DECIMAL,
    ADD COLUMN collateral_value_inr DECIMAL,
    ADD COLUMN current_ltv_percent DECIMAL,
    ADD COLUMN last_price_update TIMESTAMP,
    ADD COLUMN margin_call_ltv_percent INT,
    ADD COLUMN liquidation_ltv_percent INT;

-- Create Loan LTV History tracking table
CREATE TABLE loan_ltv_history (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    btc_price_inr DECIMAL NOT NULL,
    collateral_value_inr DECIMAL NOT NULL,
    ltv_percent DECIMAL NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_loan_ltv_history_loan FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
);
