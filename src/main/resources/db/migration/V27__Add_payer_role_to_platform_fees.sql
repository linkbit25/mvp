-- Add payer_role to platform_fees table
ALTER TABLE platform_fees ADD COLUMN payer_role VARCHAR(20);

-- Update existing fees to BORROWER (historically only borrowers paid)
UPDATE platform_fees SET payer_role = 'BORROWER' WHERE payer_role IS NULL;

-- Make it non-nullable
ALTER TABLE platform_fees ALTER COLUMN payer_role SET NOT NULL;
