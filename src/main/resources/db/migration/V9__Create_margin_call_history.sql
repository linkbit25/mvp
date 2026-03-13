CREATE TABLE loan_margin_calls (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_margin_calls_loan FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
);
