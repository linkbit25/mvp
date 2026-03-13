ALTER TABLE loans ADD COLUMN IF NOT EXISTS liquidation_executed_at TIMESTAMP;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS liquidation_price_inr DECIMAL(19, 2);
ALTER TABLE loans ADD COLUMN IF NOT EXISTS lender_repayment_amount DECIMAL(19, 2);
ALTER TABLE loans ADD COLUMN IF NOT EXISTS borrower_return_amount DECIMAL(19, 2);
ALTER TABLE loans ADD COLUMN IF NOT EXISTS liquidation_penalty_amount DECIMAL(19, 2);
