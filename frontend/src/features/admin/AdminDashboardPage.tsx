import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import api from '@/services/api';
import { useAuthStore } from '@/store/authStore';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  ShieldCheck, Clock, AlertTriangle, CheckCircle2,
  ChevronRight, CreditCard, Bitcoin, Gavel
} from 'lucide-react';

// ─── Admin Dashboard Page ────────────────────────────────────────────────────
export const AdminDashboardPage = () => {
  const navigate = useNavigate();
  const user = useAuthStore((state: any) => state.user);
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'repayments' | 'deposits' | 'risk'>('repayments');

  // Redirect non-admins
  if (user?.role !== 'ADMIN') {
    navigate('/dashboard');
    return null;
  }

  return (
    <div className="max-w-7xl mx-auto py-10 px-6 space-y-8">
      {/* Header */}
      <div className="flex items-center gap-4">
        <div className="p-3 bg-indigo-100 rounded-2xl">
          <ShieldCheck className="h-8 w-8 text-indigo-600" />
        </div>
        <div>
          <h1 className="text-3xl font-black text-slate-900 uppercase tracking-tight">Admin Panel</h1>
          <p className="text-slate-500 font-medium text-sm mt-1">Loan oversight, verification, and risk monitoring</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="bg-slate-100 p-1.5 rounded-2xl flex gap-2 w-fit">
        {([
          { key: 'repayments', label: 'Repayments', icon: CreditCard },
          { key: 'deposits', label: 'Collateral', icon: Bitcoin },
          { key: 'risk', label: 'Risk Monitor', icon: AlertTriangle },
        ] as const).map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-black text-xs uppercase tracking-widest transition-all ${
              activeTab === key ? 'bg-white shadow-sm text-indigo-700' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Icon className="h-3.5 w-3.5" />
            {label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'repayments' && <RepaymentsTab queryClient={queryClient} />}
      {activeTab === 'deposits' && <DepositsTab queryClient={queryClient} />}
      {activeTab === 'risk' && <RiskTab />}
    </div>
  );
};

// ─── Repayments Tab ──────────────────────────────────────────────────────────
const RepaymentsTab = ({ queryClient }: { queryClient: any }) => {
  const { data: repayments = [], isLoading } = useQuery({
    queryKey: ['admin', 'pending-repayments'],
    queryFn: async () => (await api.get('/admin/repayments/pending')).data,
    refetchInterval: 30000,
  });

  const verifyMutation = useMutation({
    mutationFn: (repaymentId: string) => api.post(`/admin/repayments/${repaymentId}/verify`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'pending-repayments'] }),
  });

  if (isLoading) return <LoadingSpinner label="Loading repayments..." />;

  return (
    <Card className="border-slate-200 rounded-[2rem] shadow-xl overflow-hidden">
      <CardHeader className="px-8 py-6 bg-white border-b border-slate-100">
        <CardTitle className="text-sm font-black uppercase tracking-widest text-slate-800 flex items-center gap-2">
          <CreditCard className="h-4 w-4 text-indigo-500" />
          Pending Repayments ({repayments.length})
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {repayments.length === 0 ? (
          <EmptyState label="No pending repayments" />
        ) : (
          <div className="divide-y divide-slate-50">
            {repayments.map((r: any) => (
              <div key={r.repaymentId} className="flex items-center justify-between px-8 py-5 hover:bg-slate-50/60 transition-all">
                <div className="space-y-1">
                  <p className="text-sm font-black text-slate-900">₹{r.amountInr?.toLocaleString()}</p>
                  <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">{r.transactionReference}</p>
                  <p className="text-[10px] text-slate-300 font-medium">{r.createdAt ? format(new Date(r.createdAt), 'MMM dd, yyyy HH:mm') : '—'}</p>
                </div>
                <div className="flex items-center gap-4">
                  {r.proofUrl && (
                    <a href={r.proofUrl} target="_blank" rel="noreferrer"
                      className="text-[10px] font-black text-indigo-500 uppercase underline underline-offset-2 hover:text-indigo-700">
                      View Proof
                    </a>
                  )}
                  <Button
                    size="sm"
                    disabled={verifyMutation.isPending}
                    onClick={() => verifyMutation.mutate(r.repaymentId)}
                    className="bg-green-600 hover:bg-green-700 text-white font-black text-[10px] uppercase tracking-widest rounded-xl h-9 px-5 shadow"
                  >
                    <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                    Verify
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
};

// ─── Deposits Tab ────────────────────────────────────────────────────────────
const DepositsTab = ({ queryClient }: { queryClient: any }) => {
  const { data: overview, isLoading } = useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: async () => (await api.get('/admin/overview')).data,
    refetchInterval: 30000,
  });

  const verifyDepositMutation = useMutation({
    mutationFn: (loanId: string) => api.post(`/admin/loans/${loanId}/verify-deposit`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'overview'] }),
  });

  if (isLoading) return <LoadingSpinner label="Loading collateral data..." />;

  const awaitingLoans = (overview?.loans ?? []).filter((l: any) =>
    ['AWAITING_COLLATERAL', 'COLLATERAL_LOCKED'].includes(l.status)
  );

  return (
    <Card className="border-slate-200 rounded-[2rem] shadow-xl overflow-hidden">
      <CardHeader className="px-8 py-6 bg-white border-b border-slate-100">
        <CardTitle className="text-sm font-black uppercase tracking-widest text-slate-800 flex items-center gap-2">
          <Bitcoin className="h-4 w-4 text-amber-500" />
          Collateral Verification ({awaitingLoans.length})
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {awaitingLoans.length === 0 ? (
          <EmptyState label="No collateral deposits to verify" />
        ) : (
          <div className="divide-y divide-slate-50">
            {awaitingLoans.map((l: any) => (
              <div key={l.loanId} className="flex items-center justify-between px-8 py-5 hover:bg-slate-50/60 transition-all">
                <div className="space-y-1">
                  <div className="flex items-center gap-3">
                    <p className="text-xs font-black text-slate-900 font-mono">#{l.loanId?.slice(0, 8)}</p>
                    <StatusBadge status={l.status} />
                  </div>
                  <p className="text-[10px] text-slate-500 font-bold uppercase">
                    {l.borrowerPseudonym} → {l.lenderPseudonym} • ₹{l.principalAmount?.toLocaleString()}
                  </p>
                  {l.escrowAddress && (
                    <p className="text-[9px] text-slate-300 font-mono break-all">{l.escrowAddress}</p>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <p className="text-xs font-black text-amber-600">{l.escrowBalanceSats ? (l.escrowBalanceSats / 1e8).toFixed(6) : '—'} BTC</p>
                    <p className="text-[9px] text-slate-300 font-medium">in escrow</p>
                  </div>
                  <Button
                    size="sm"
                    disabled={verifyDepositMutation.isPending || l.status === 'COLLATERAL_LOCKED'}
                    onClick={() => verifyDepositMutation.mutate(l.loanId)}
                    className="bg-amber-500 hover:bg-amber-600 text-white font-black text-[10px] uppercase tracking-widest rounded-xl h-9 px-5 shadow"
                  >
                    <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                    {l.status === 'COLLATERAL_LOCKED' ? 'Locked' : 'Verify'}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
};

// ─── Risk Tab ────────────────────────────────────────────────────────────────
const RiskTab = () => {
  const navigate = useNavigate();
  const { data: overview, isLoading } = useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: async () => (await api.get('/admin/overview')).data,
    refetchInterval: 30000,
  });

  if (isLoading) return <LoadingSpinner label="Loading risk data..." />;

  const riskLoans = (overview?.loans ?? []).filter((l: any) =>
    ['MARGIN_CALL', 'LIQUIDATION_ELIGIBLE', 'DISPUTE_OPEN'].includes(l.status)
  );

  return (
    <Card className="border-slate-200 rounded-[2rem] shadow-xl overflow-hidden">
      <CardHeader className="px-8 py-6 bg-white border-b border-slate-100">
        <CardTitle className="text-sm font-black uppercase tracking-widest text-red-700 flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 text-red-500" />
          Active Risk Cases ({riskLoans.length})
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {riskLoans.length === 0 ? (
          <EmptyState label="No risk cases — all clear" />
        ) : (
          <div className="divide-y divide-slate-50">
            {riskLoans.map((l: any) => (
              <div key={l.loanId}
                onClick={() => navigate(`/loans/${l.loanId}`)}
                className="flex items-center justify-between px-8 py-5 hover:bg-red-50/40 transition-all cursor-pointer group"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-3">
                    <p className="text-xs font-black text-slate-900 font-mono">#{l.loanId?.slice(0, 8)}</p>
                    <StatusBadge status={l.status} />
                  </div>
                  <p className="text-[10px] text-slate-500 font-bold uppercase">
                    {l.borrowerPseudonym} → {l.lenderPseudonym} • ₹{l.principalAmount?.toLocaleString()}
                  </p>
                  <p className="text-[10px] text-slate-400 font-medium">
                    LTV: {l.currentLtvPercent ?? '—'}% / Margin: {l.marginCallLtvPercent}% / Liq: {l.liquidationLtvPercent}%
                  </p>
                </div>
                <ChevronRight className="h-4 w-4 text-slate-300 group-hover:text-slate-600 transition-colors" />
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
};

// ─── Shared helpers ──────────────────────────────────────────────────────────
const StatusBadge = ({ status }: { status: string }) => {
  const colors: Record<string, string> = {
    AWAITING_COLLATERAL: 'bg-amber-100 text-amber-700',
    COLLATERAL_LOCKED: 'bg-blue-100 text-blue-700',
    MARGIN_CALL: 'bg-orange-100 text-orange-700',
    LIQUIDATION_ELIGIBLE: 'bg-red-100 text-red-700',
    DISPUTE_OPEN: 'bg-purple-100 text-purple-700',
  };
  return (
    <Badge className={`rounded-full px-3 py-0.5 text-[9px] font-black uppercase border-none ${colors[status] ?? 'bg-slate-100 text-slate-600'}`}>
      {status}
    </Badge>
  );
};

const EmptyState = ({ label }: { label: string }) => (
  <div className="flex flex-col items-center justify-center py-16 text-center">
    <Gavel className="h-10 w-10 text-slate-100 mb-3" />
    <p className="text-[10px] font-black text-slate-300 uppercase tracking-widest">{label}</p>
  </div>
);

const LoadingSpinner = ({ label }: { label: string }) => (
  <div className="flex flex-col items-center justify-center p-16 gap-3">
    <Clock className="animate-spin h-8 w-8 text-indigo-400" />
    <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">{label}</p>
  </div>
);
