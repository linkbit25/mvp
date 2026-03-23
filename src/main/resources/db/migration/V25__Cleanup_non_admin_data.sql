-- V25: Cleanup all non-admin data
-- Preserving admin user ID: '123e4567-e89b-12d3-a456-426614174001'

-- Clear child tables first to avoid FK violations
DELETE FROM notifications WHERE user_id != '123e4567-e89b-12d3-a456-426614174001';
DELETE FROM password_reset_token WHERE user_id != '123e4567-e89b-12d3-a456-426614174001';
DELETE FROM platform_fees;
DELETE FROM loan_ledger;
DELETE FROM loan_repayments;
DELETE FROM loan_emis;
DELETE FROM loan_audit_log;
DELETE FROM loan_margin_calls;
DELETE FROM loan_ltv_history;
DELETE FROM bitcoin_transactions;
DELETE FROM escrow_accounts;
DELETE FROM loan_liquidations;
DELETE FROM collateral_releases;
DELETE FROM negotiation_messages;
DELETE FROM loan_offers;
DELETE FROM loans;

-- Clear user related tables
DELETE FROM user_kyc_details WHERE user_id != '123e4567-e89b-12d3-a456-426614174001';

-- Finally clear users
DELETE FROM users WHERE id != '123e4567-e89b-12d3-a456-426614174001';
