-- Remove entry_type check constraint to allow more flexibility
ALTER TABLE loan_ledger DROP CONSTRAINT IF EXISTS loan_ledger_entry_type_check;
