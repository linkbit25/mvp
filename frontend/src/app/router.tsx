import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { MarketplacePage } from '@/features/marketplace/MarketplacePage';
import { KycPage } from '@/features/kyc/KycPage';
import { LoanNegotiationPage } from '@/features/loans/LoanNegotiationPage';
import { LoanAgreementPage } from '@/features/loans/LoanAgreementPage';
import { FeePaymentPage } from '@/features/loans/FeePaymentPage';
import { CollateralDepositPage } from '@/features/loans/CollateralDepositPage';
import { DisbursementPage } from '@/features/loans/DisbursementPage';
import { RepaymentPage } from '@/features/loans/RepaymentPage';
import { LoanClosurePage } from '@/features/loans/LoanClosurePage';
import { AdminDashboardPage } from '@/features/admin/AdminDashboardPage';

const Marketplace = () => <MarketplacePage />;
const Kyc = () => <KycPage />;

const LoanDetail = () => <LoanNegotiationPage />;
const LoanAgreement = () => <LoanAgreementPage />;
const FeePayment = () => <FeePaymentPage />;
const CollateralDeposit = () => <CollateralDepositPage />;
const Disbursement = () => <DisbursementPage />;
const Repayment = () => <RepaymentPage />;
const LoanClosure = () => <LoanClosurePage />;
const AdminDashboard = () => <AdminDashboardPage />;

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        element: <Layout />,
        children: [
          {
            index: true,
            element: <Navigate to="/dashboard" replace />,
          },
          {
            path: 'dashboard',
            element: <DashboardPage />,
          },
          {
            path: 'marketplace',
            element: <Marketplace />,
          },
          {
            path: 'kyc',
            element: <Kyc />,
          },
          {
            path: 'loans/:id',
            element: <LoanDetail />,
          },
          {
            path: 'loans/:id/agreement',
            element: <LoanAgreement />,
          },
          {
            path: 'loans/:id/fee',
            element: <FeePayment />,
          },
          {
            path: 'loans/:id/collateral',
            element: <CollateralDeposit />,
          },
          {
            path: 'loans/:id/disbursement',
            element: <Disbursement />,
          },
          {
            path: 'loans/:id/repay',
            element: <Repayment />,
          },
          {
            path: 'loans/:id/closure',
            element: <LoanClosure />,
          },
          {
            path: 'admin',
            element: <AdminDashboard />,
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/dashboard" replace />,
  },
]);
