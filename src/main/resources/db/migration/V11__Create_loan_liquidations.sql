CREATE TABLE IF NOT EXISTS loan_liquidations (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    btc_price_inr DECIMAL(19, 2) NOT NULL,
    collateral_value_inr DECIMAL(19, 2) NOT NULL,
    lender_repaid DECIMAL(19, 2) NOT NULL,
    borrower_returned DECIMAL(19, 2) NOT NULL,
    liquidation_penalty DECIMAL(19, 2) NOT NULL,
    executed_at TIMESTAMP NOT NULL
);
