-- Standardize user roles and fix constraints
-- Fix default role and update existing users
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
UPDATE users SET role = 'USER' WHERE role = 'BORROWER';

-- Drop any existing check constraint on kyc_status if we need to expand it (already has SUBMITTED in V1, but let's be safe)
-- H2 doesn't always support easy dropping of constraints by name if they are auto-generated, 
-- but we can try to find them or just add a more general one if needed.
-- However, V1 already includes SUBMITTED.

-- Ensure role has a proper check constraint now that we've standardized
ALTER TABLE users ADD CONSTRAINT user_role_check CHECK (role IN ('USER', 'ADMIN', 'SYSTEM', 'BORROWER', 'LENDER'));
