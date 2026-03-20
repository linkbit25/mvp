CREATE TABLE loan_audit_log (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    previous_state VARCHAR(255),
    new_state VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_audit
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE
);
