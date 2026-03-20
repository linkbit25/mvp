import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import api from '@/services/api';
import type { LoanSummary } from '@/features/loans/types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import type { BadgeProps } from '@/components/ui/badge';
import { 
  Wallet, 
  HandCoins, 
  Activity, 
  AlertTriangle, 
  ChevronRight,
  Plus,
  Loader2,
  RefreshCcw
} from 'lucide-react';
import { useAuthStore } from '@/store/authStore';

const StatCard = ({ title, value, subValue, icon: Icon, colorClass }: any) => (
  <Card className="border-slate-200 hover:shadow-md transition-all duration-200">
    <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
      <CardTitle className="text-sm font-medium text-slate-500 uppercase tracking-wider">{title}</CardTitle>
      <div className={`p-2 rounded-lg ${colorClass}`}>
        <Icon className="h-4 w-4" />
      </div>
    </CardHeader>
    <CardContent>
      <div className="text-2xl font-bold text-slate-900">{value}</div>
      <p className="text-xs text-slate-500 mt-1">{subValue}</p>
    </CardContent>
  </Card>
);

const getStatusVariant = (status: string): BadgeProps['variant'] => {
  switch (status) {
    case 'ACTIVE': return 'success';
    case 'MARGIN_CALL': return 'destructive';
    case 'LIQUIDATION_ELIGIBLE': return 'destructive';
    case 'NEGOTIATING': return 'warning';
    case 'AWAITING_SIGNATURES': return 'warning';
    case 'AWAITING_FEE': return 'info';
    case 'AWAITING_COLLATERAL': return 'info';
    case 'CLOSED': return 'secondary';
    case 'REPAID': return 'secondary';
    case 'CANCELLED': return 'outline';
    default: return 'default';
  }
};

const getActionLabel = (status: string): string => {
  switch (status) {
    case 'NEGOTIATING': return 'Continue';
    case 'AWAITING_SIGNATURES': return 'Sign';
    case 'AWAITING_FEE': return 'Pay Fee';
    case 'AWAITING_COLLATERAL': return 'Deposit';
    case 'ACTIVE': return 'Repay';
    case 'REPAID': return 'View';
    case 'CLOSED': return 'View';
    case 'MARGIN_CALL': return 'Take Action';
    case 'LIQUIDATION_ELIGIBLE': return 'Take Action';
    default: return 'View';
  }
};

export const DashboardPage = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());
  const [timeAgo, setTimeAgo] = useState<string>('0s ago');

  const { data: loans, isLoading, error, dataUpdatedAt } = useQuery<LoanSummary[]>({
    queryKey: ['my-loans'],
    queryFn: async () => {
      const response = await api.get('/loans/mine');
      return response.data.content || response.data;
    },
    refetchInterval: 30000,
  });

  useEffect(() => {
    if (dataUpdatedAt) {
      setLastUpdated(new Date(dataUpdatedAt));
    }
  }, [dataUpdatedAt]);

  useEffect(() => {
    const updateTimer = setInterval(() => {
      const seconds = Math.floor((new Date().getTime() - lastUpdated.getTime()) / 1000);
      if (seconds < 60) {
        setTimeAgo(`${seconds}s ago`);
      } else {
        setTimeAgo(`${Math.floor(seconds / 60)}m ago`);
      }
    }, 1000);
    return () => clearInterval(updateTimer);
  }, [lastUpdated]);

  if (isLoading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 text-center text-red-600 bg-red-50 rounded-xl border border-red-200">
        Failed to load dashboard data. Please try again later.
      </div>
    );
  }

  // Calculations & Sorting
  const sortedLoans = [...(loans || [])].sort((a, b) => {
    const getPriority = (status: string) => {
      if (['MARGIN_CALL', 'LIQUIDATION_ELIGIBLE'].includes(status)) return 0;
      if (status === 'ACTIVE') return 1;
      return 2;
    };
    return getPriority(a.status) - getPriority(b.status);
  });

  const totalBorrowed = loans?.filter(l => l.role === 'BORROWER' && l.status !== 'CANCELLED')
    .reduce((sum, l) => sum + (l.principalAmount || 0), 0) || 0;
  
  const totalLent = loans?.filter(l => l.role === 'LENDER' && l.status !== 'CANCELLED')
    .reduce((sum, l) => sum + (l.principalAmount || 0), 0) || 0;
  
  const activeCount = loans?.filter(l => ['ACTIVE', 'MARGIN_CALL', 'LIQUIDATION_ELIGIBLE'].includes(l.status)).length || 0;
  
  const atRiskCount = loans?.filter(l => ['MARGIN_CALL', 'LIQUIDATION_ELIGIBLE'].includes(l.status)).length || 0;

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 tracking-tight">Financial Overview</h1>
          <div className="flex items-center gap-2 mt-1 px-0.5">
            <p className="text-slate-500 text-sm">Real-time status of your borrowing and lending</p>
            <span className="text-slate-300">•</span>
            <div className="flex items-center gap-1.5 text-xs text-indigo-600 font-medium">
              <RefreshCcw className="h-3 w-3 animate-spin-slow" />
              Updated {timeAgo}
            </div>
          </div>
        </div>
        {user?.role === 'LENDER' ? (
          <Link to="/offers/create">
            <Button className="bg-indigo-600 hover:bg-indigo-700 shadow-sm">
              <Plus className="h-4 w-4 mr-2" />
              Create Offer
            </Button>
          </Link>
        ) : (
          <Link to="/marketplace">
            <Button className="bg-indigo-600 hover:bg-indigo-700 shadow-sm">
              <Plus className="h-4 w-4 mr-2" />
              New Loan Request
            </Button>
          </Link>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard 
          title="Total Borrowed" 
          value={`₹${totalBorrowed.toLocaleString()}`} 
          subValue="Principal across all loans"
          icon={Wallet}
          colorClass="bg-red-50 text-red-600"
        />
        <StatCard 
          title="Total Lent" 
          value={`₹${totalLent.toLocaleString()}`} 
          subValue="Earning interest"
          icon={HandCoins}
          colorClass="bg-emerald-50 text-emerald-600"
        />
        <StatCard 
          title="Active Loans" 
          value={activeCount.toString()} 
          subValue="In repayment cycle"
          icon={Activity}
          colorClass="bg-blue-50 text-blue-600"
        />
        <StatCard 
          title="At Risk" 
          value={atRiskCount.toString()} 
          subValue={atRiskCount > 0 ? "Requires attention" : "Everything healthy"}
          icon={AlertTriangle}
          colorClass={atRiskCount > 0 ? "bg-amber-50 text-amber-600" : "bg-slate-50 text-slate-400"}
        />
      </div>

      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-slate-900">Your Loans</h2>
        
        {sortedLoans.length > 0 ? (
          <div className="grid gap-4">
            {sortedLoans.map((loan) => {
              const isAtRisk = ['MARGIN_CALL', 'LIQUIDATION_ELIGIBLE'].includes(loan.status);
              return (
                <Card 
                  key={loan.loanId} 
                  className={`border-slate-200 hover:border-indigo-300 transition-all duration-200 group cursor-pointer overflow-hidden ${
                    isAtRisk ? 'border-red-300 shadow-[0_0_15px_rgba(239,68,68,0.15)] ring-1 ring-red-100' : ''
                  }`}
                  onClick={() => navigate(`/loans/${loan.loanId}`)}
                >
                  <CardContent className="p-0">
                    <div className="flex flex-col md:flex-row items-center justify-between p-4 sm:p-6 gap-4">
                      <div className="flex items-center gap-4 w-full md:w-auto">
                        <div className={`w-10 h-10 rounded-full flex items-center justify-center font-bold shrink-0 ${
                          loan.role === 'BORROWER' ? 'bg-indigo-100 text-indigo-700' : 'bg-emerald-100 text-emerald-700'
                        }`}>
                          {loan.role[0]}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-semibold text-slate-900 truncate">ID: {loan.loanId.substring(0, 8)}...</span>
                            <Badge variant={getStatusVariant(loan.status)} className={isAtRisk ? 'animate-pulse' : ''}>
                              {loan.status.replace(/_/g, ' ')}
                            </Badge>
                          </div>
                          <div className="text-xs text-slate-500 mt-1 capitalize leading-relaxed">
                            Role: {loan.role.toLowerCase()} • With {loan.counterpartyPseudonym}
                          </div>
                        </div>
                      </div>
                      
                      <div className="flex items-center justify-between md:justify-end gap-8 w-full md:w-auto mt-2 md:mt-0 pt-4 md:pt-0 border-t md:border-0 border-slate-100">
                        <div className="text-right">
                          <div className="text-xs text-slate-500 mb-0.5 uppercase tracking-tighter">Outstanding</div>
                          <div className="font-bold text-slate-900 leading-none">
                            ₹{loan.totalOutstanding.toLocaleString()}
                          </div>
                        </div>
                        <Button 
                          variant="outline" 
                          className="group-hover:bg-indigo-50 group-hover:text-indigo-600 transition-colors flex items-center gap-2 pr-2 h-9"
                        >
                          {getActionLabel(loan.status)}
                          <ChevronRight className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        ) : (
          <Card className="border-dashed border-slate-300 bg-slate-50/50">
            <CardHeader className="flex flex-col items-center justify-center py-12 text-center">
              <div className="bg-slate-200 p-4 rounded-full mb-4">
                <Activity className="h-8 w-8 text-slate-400" />
              </div>
              <CardTitle className="text-lg font-semibold text-slate-900">No loans yet</CardTitle>
              <CardDescription className="text-slate-500 max-w-xs mt-2 mb-6">
                You haven't participated in any loan offers yet. Start exploring the marketplace!
              </CardDescription>
              <Link to="/marketplace">
                <Button className="bg-indigo-600 hover:bg-indigo-700">Explore Marketplace</Button>
              </Link>
            </CardHeader>
          </Card>
        )}
      </div>
    </div>
  );
};
