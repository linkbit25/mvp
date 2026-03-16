-- Insert admin user
INSERT INTO users (id, email, password_hash, phone_number, pseudonym, kyc_status, created_at, updated_at)
VALUES ('123e4567-e89b-12d3-a456-426614174001', 'admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', '9999999999', 'Admin', 'VERIFIED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert KYC details for admin
INSERT INTO user_kyc_details (user_id, full_legal_name, bank_account_number, ifsc, upi_id, national_id_hash, created_at)
VALUES ('123e4567-e89b-12d3-a456-426614174001', 'Admin User', '999999999', 'ADMIN001', 'admin@upi', 'adminhash', CURRENT_TIMESTAMP);
