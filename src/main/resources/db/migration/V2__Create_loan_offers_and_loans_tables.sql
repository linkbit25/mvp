CREATE TYPE loan_offer_status AS ENUM ('OPEN', 'PAUSED', 'CLOSED');
CREATE TYPE loan_status AS ENUM ('NEGOTIATING', 'AGREED', 'ACTIVE', 'DEFAULTED', 'CLOSED');

CREATE TABLE loan_offers (
    id UUID PRIMARY KEY,
    lender_id UUID NOT NULL,
    loan_amount_inr DECIMAL(19, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) NOT NULL,
    expected_ltv_percent INT NOT NULL,
    tenure_days INT NOT NULL,
    status loan_offer_status NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lender
        FOREIGN KEY(lender_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE loans (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    lender_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    principal_amount DECIMAL(19, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) NOT NULL,
    tenure_days INT NOT NULL,
    status loan_status NOT NULL DEFAULT 'NEGOTIATING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_offer
        FOREIGN KEY(offer_id)
        REFERENCES loan_offers(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_loan_lender
        FOREIGN KEY(lender_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_loan_borrower
        FOREIGN KEY(borrower_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);
