import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import api from '@/services/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { 
  CheckCircle2, 
  ShieldCheck, 
  TrendingUp, 
  TrendingDown, 
  Clock, 
  LayoutDashboard,
  Gem,
  ArrowUpRight
} from 'lucide-react';
import { format } from 'date-fns';

export const LoanClosurePage = () => {
  const { id: loanId } = useParams();
  const navigate = useNavigate();

  const { data: loan, isLoading } = useQuery({
    queryKey: ['loan', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/details`);
      return res.data;
    },
    refetchInterval: 5000,
  });

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-32 space-y-6">
        <Clock className="animate-spin h-12 w-12 text-indigo-600" />
        <p className="text-slate-500 font-bold uppercase tracking-widest text-xs">Finalizing Records...</p>
      </div>
    );
  }

  if (!loan) return <div className="p-20 text-center font-bold text-slate-400 uppercase">Loan data not found.</div>;

  const isClosed = loan.status === 'CLOSED';
  const interestPaid = loan.totalRepaymentAmount - loan.principalAmount;
  const btcReleased = loan.collateralReleasedBtc || 0;

  return (
    <div className="max-w-4xl mx-auto py-16 px-6">
      <div className="flex flex-col items-center text-center space-y-8 animate-in fade-in slide-in-from-bottom-8 duration-1000">
        
        {/* SUCCESS BADGE & TITLE */}
        <div className="flex flex-col items-center space-y-4">
          <div className="bg-green-100 p-6 rounded-full shadow-inner ring-4 ring-green-50">
            <CheckCircle2 className="h-16 w-16 text-green-600" />
          </div>
          <div>
            <Badge className="mb-4 bg-indigo-50 text-indigo-600 border-none px-4 py-1.5 rounded-full font-black text-[10px] uppercase tracking-widest">
              Final Stage Completed
            </Badge>
            <h1 className="text-4xl font-black text-slate-900 tracking-tight lg:text-6xl uppercase">Loan Successfully Closed</h1>
            <p className="mt-3 text-slate-500 font-medium italic max-w-lg mx-auto">
              Your financial obligation is settled and your collateral {isClosed ? 'has been released' : 'is being processed for release'}.
            </p>
          </div>
        </div>

        {/* FINANCIAL SUMMARY HUD */}
        <div className="w-full grid grid-cols-1 md:grid-cols-3 gap-6 mt-12">
           <Card className="bg-slate-900 text-white rounded-[2.5rem] border-none shadow-2xl relative overflow-hidden group">
              <div className="absolute top-0 right-0 p-8 opacity-10 group-hover:scale-110 transition-transform">
                <TrendingDown className="h-20 w-20" />
              </div>
              <CardHeader className="p-10 pb-2">
                <CardTitle className="text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">Total Borrowed</CardTitle>
              </CardHeader>
              <CardContent className="p-10 pt-0 text-left">
                <p className="text-4xl font-black tracking-tight flex items-baseline gap-1">
                  <span className="text-xl opacity-40">₹</span>
                  {loan.principalAmount.toLocaleString()}
                </p>
              </CardContent>
           </Card>

           <Card className="bg-white rounded-[2.5rem] shadow-xl border-slate-100 overflow-hidden relative group transition-all hover:scale-[1.02]">
              <CardHeader className="p-10 pb-2">
                <CardTitle className="text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">Interest Paid</CardTitle>
              </CardHeader>
              <CardContent className="p-10 pt-0 text-left">
                <p className="text-4xl font-black text-indigo-600 tracking-tight flex items-baseline gap-1">
                  <span className="text-xl opacity-40">₹</span>
                  {interestPaid.toLocaleString()}
                </p>
              </CardContent>
           </Card>

           <Card className="bg-white rounded-[2.5rem] shadow-xl border-slate-100 overflow-hidden relative group transition-all hover:scale-[1.02]">
              <div className="absolute top-0 right-0 p-10 opacity-5 group-hover:scale-110 transition-transform">
                <TrendingUp className="h-20 w-20 text-green-600" />
              </div>
              <CardHeader className="p-10 pb-2">
                <CardTitle className="text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">Total Repaid</CardTitle>
              </CardHeader>
              <CardContent className="p-10 pt-0 text-left">
                <p className="text-4xl font-black text-green-600 tracking-tight flex items-baseline gap-1">
                  <span className="text-xl opacity-40">₹</span>
                  {loan.totalRepaymentAmount.toLocaleString()}
                </p>
              </CardContent>
           </Card>
        </div>

        {/* COLLATERAL RELEASE SECTION */}
        <div className="w-full mt-8">
           <Card className="border-slate-200 shadow-2xl rounded-[3rem] bg-white overflow-hidden uppercase">
              <div className="grid grid-cols-1 md:grid-cols-2">
                <div className="p-12 bg-slate-50 flex flex-col items-center justify-center text-center space-y-4 border-r border-slate-100">
                   <div className="bg-white p-6 rounded-[2rem] shadow-sm">
                      <Gem className="h-12 w-12 text-indigo-500" />
                   </div>
                   <div>
                      <p className="text-[10px] font-black text-slate-400 tracking-widest mb-1">Total Collateral Released</p>
                      <h3 className="text-3xl font-black text-slate-900 tracking-tight">{btcReleased.toFixed(8)} BTC</h3>
                      <p className="text-[9px] text-slate-400 font-bold mt-1 leading-none italic">Escrow ID: {loanId?.slice(0, 12)}</p>
                   </div>
                </div>

                <div className="p-12 flex flex-col justify-center space-y-8">
                   <div className="flex items-center justify-between">
                      <p className="text-xs font-black text-slate-500 tracking-widest">Status</p>
                      <Badge className={`rounded-full px-5 py-1.5 text-[10px] font-black tracking-tight ${
                        isClosed ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700 animate-pulse'
                      }`}>
                         {isClosed ? 'RELEASED' : 'PENDING RELEASE'}
                      </Badge>
                   </div>

                   <div className="flex items-center justify-between">
                      <p className="text-xs font-black text-slate-500 tracking-widest">Released On</p>
                      <p className="text-sm font-black text-slate-900">
                        {loan.collateralReleasedAt ? format(new Date(loan.collateralReleasedAt), 'MMM dd, yyyy • HH:mm') : 'PROCESSING...'}
                      </p>
                   </div>

                   <div className="p-5 bg-indigo-50 rounded-2xl border border-indigo-100 flex items-start gap-4">
                      <ShieldCheck className="h-5 w-5 text-indigo-400 shrink-0 mt-0.5" />
                      <p className="text-[10px] text-indigo-900 font-black leading-snug tracking-tight text-left italic">
                        All collateral has been returned to your registered Bitcoin wallet. Transfer verification available on-chain.
                      </p>
                   </div>
                </div>
              </div>
           </Card>
        </div>

        {/* FINAL ACTIONS */}
        <div className="w-full flex flex-col md:flex-row gap-6 mt-12 pb-24">
           <Button 
            onClick={() => navigate('/dashboard')}
            className="flex-1 h-20 bg-slate-900 hover:bg-black text-white rounded-3xl font-black text-base shadow-2xl transition-all active:scale-95 group uppercase tracking-widest"
           >
              <LayoutDashboard className="h-5 w-5 mr-3" />
              Return to Dashboard
           </Button>
           
           <Button 
            variant="outline"
            className="flex-1 h-20 border-slate-200 border-2 rounded-3xl font-black text-base transition-all active:scale-95 hover:bg-slate-50 uppercase tracking-widest"
           >
              Download Settlement PDF
              <ArrowUpRight className="h-5 w-5 ml-3 opacity-30" />
           </Button>
        </div>

      </div>
    </div>
  );
};
