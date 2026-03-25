export const getLoanRoute = (loanId: string, status: string): string => {
  switch (status) {
    case 'NEGOTIATING': return `/loans/${loanId}`;
    case 'AWAITING_SIGNATURES': return `/loans/${loanId}/agreement`;
    case 'AWAITING_FEE': return `/loans/${loanId}/fee`;
    case 'AWAITING_COLLATERAL': return `/loans/${loanId}/collateral`;
    case 'COLLATERAL_LOCKED':
    case 'DISPUTE_OPEN': return `/loans/${loanId}/disbursement`;
    case 'ACTIVE':
    case 'MARGIN_CALL':
    case 'LIQUIDATION_ELIGIBLE': return `/loans/${loanId}/repay`;
    case 'REPAID':
    case 'CLOSED': return `/loans/${loanId}/closure`;
    default: return `/loans/${loanId}`;
  }
};

export const getLoanStatusLabel = (status: string): string => {
  switch (status) {
    case 'NEGOTIATING': return 'Negotiating';
    case 'AWAITING_SIGNATURES': return 'Sign Agreement';
    case 'AWAITING_FEE': return 'Pay Fee';
    case 'AWAITING_COLLATERAL': return 'Deposit Collateral';
    case 'COLLATERAL_LOCKED': return 'Disbursement';
    case 'ACTIVE': return 'Repay';
    case 'REPAID': return 'View';
    case 'CLOSED': return 'View';
    case 'MARGIN_CALL': return 'Take Action';
    case 'LIQUIDATION_ELIGIBLE': return 'Take Action';
    default: return 'View';
  }
};

export const getActionLabel = (status: string): string => {
  switch (status) {
    case 'NEGOTIATING': return 'Continue';
    case 'AWAITING_SIGNATURES': return 'Sign';
    case 'AWAITING_FEE': return 'Pay Fee';
    case 'AWAITING_COLLATERAL': return 'Deposit';
    case 'COLLATERAL_LOCKED':
    case 'DISPUTE_OPEN': return 'Disbursement';
    case 'ACTIVE': return 'Repay';
    case 'REPAID': return 'View';
    case 'CLOSED': return 'View';
    case 'MARGIN_CALL': return 'Take Action';
    case 'LIQUIDATION_ELIGIBLE': return 'Take Action';
    default: return 'View';
  }
};
