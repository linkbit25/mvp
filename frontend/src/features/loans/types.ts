export type LoanStatus =
  | 'NEGOTIATING'
  | 'AWAITING_SIGNATURES'
  | 'AWAITING_FEE'
  | 'AWAITING_COLLATERAL'
  | 'COLLATERAL_LOCKED'
  | 'AGREED'
  | 'ACTIVE'
  | 'DEFAULTED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'DISPUTE_OPEN'
  | 'LIQUIDATED'
  | 'REPAID'
  | 'MARGIN_CALL'
  | 'LIQUIDATION_ELIGIBLE';

export type RepaymentType = 'BULLET' | 'EMI' | 'FLEXIBLE';

export interface LoanSummary {
  loanId: string;
  role: 'LENDER' | 'BORROWER';
  status: LoanStatus;
  borrowerPseudonym: string;
  lenderPseudonym: string;
  counterpartyPseudonym: string;
  principalAmount: number;
  interestRate: number;
  tenureDays: number;
  repaymentType: RepaymentType;
  emiCount?: number;
  emiAmount?: number;
  totalRepaymentAmount: number;
  totalOutstanding: number;
  expectedLtvPercent: number;
  currentLtvPercent: number;
  collateralBtcAmount: number;
  collateralValueInr: number;
  createdAt: string;
  updatedAt: string;
}
