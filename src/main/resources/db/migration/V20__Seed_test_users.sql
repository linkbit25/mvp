-- Insert borrower
INSERT INTO users (id, email, password_hash, phone_number, pseudonym, kyc_status, role, is_email_verified, created_at, updated_at)
VALUES ('223e4567-e89b-12d3-a456-426614174002', 'borrower@linkbit.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', '8877665544', 'SatoshiBorrower', 'VERIFIED', 'BORROWER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert lender
INSERT INTO users (id, email, password_hash, phone_number, pseudonym, kyc_status, role, is_email_verified, created_at, updated_at)
VALUES ('323e4567-e89b-12d3-a456-426614174003', 'lender@linkbit.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', '9988776655', 'LenderNode', 'VERIFIED', 'LENDER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Also update admin to have a valid email format if needed, but let's just use the ones above
UPDATE users SET email = 'admin@linkbit.com' WHERE email = 'admin';
