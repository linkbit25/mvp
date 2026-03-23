export interface Offer {
  offer_id: string;
  lender_id: string;
  lender_pseudonym: string;
  loan_amount: number;
  interest_rate: number;
  expected_ltv: number;
  tenure_days: number;
}
