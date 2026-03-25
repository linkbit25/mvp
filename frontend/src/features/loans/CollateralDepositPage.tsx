import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/services/api';
import { Card, CardContent, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { 
  Bitcoin, 
  QrCode, 
  Copy, 
  CheckCircle2, 
  ShieldAlert, 
  Clock, 
  ArrowRight,
  ExternalLink,
  Info
} from 'lucide-react';
import { useState, useEffect } from 'react';
import { getLoanRoute } from './loanRoutes';

export const CollateralDepositPage = () => {
  const { id: loanId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [amountBtc, setAmountBtc] = useState('');
  const [txId, setTxId] = useState('');
  const [hasSubmitted, setHasSubmitted] = useState(false);
  const [escrow, setEscrow] = useState<{ escrow_address: string } | null>(null);

  // 1. Fetch Loan Details (Polling)
  const { data: loan, isLoading: isLoanLoading } = useQuery({
    queryKey: ['loan', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/details`);
      return res.data;
    },
    refetchInterval: 5000,
  });

  // 2. Fetch BTC Price (Polling)
  const { data: btcPriceData } = useQuery({
    queryKey: ['btcPrice'],
    queryFn: async () => {
      const res = await api.get('/btc/price');
      return res.data;
    },
    refetchInterval: 30000,
  });

  // 3. Generate Escrow Address via useMutation (called ONCE on mount)
  const generateEscrowMutation = useMutation({
    mutationFn: async () => {
      const res = await api.post(`/loans/${loanId}/escrow/generate`);
      return res.data;
    },
    onSuccess: (data) => {
      setEscrow(data);
    },
  });

  // Trigger escrow generation exactly once when loan enters AWAITING_COLLATERAL
  useEffect(() => {
    if (loanId && loan?.status === 'AWAITING_COLLATERAL' && !escrow && !generateEscrowMutation.isPending) {
      generateEscrowMutation.mutate();
    }
  }, [loanId, loan?.status]);

  // 4. Submit Deposit Mutation
  const depositMutation = useMutation({
    mutationFn: (data: { amount_btc: number }) => api.post(`/loans/${loanId}/deposit`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
      setHasSubmitted(true);
    }
  });

  const handleCopy = () => {
    if (escrow?.escrow_address) {
      navigator.clipboard.writeText(escrow.escrow_address);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (amountBtc) {
      depositMutation.mutate({ amount_btc: parseFloat(amountBtc) });
    }
  };

  if (isLoanLoading || generateEscrowMutation.isPending) {
    return (
      <div className="flex flex-col items-center justify-center p-24 space-y-4">
        <Clock className="animate-spin h-10 w-10 text-indigo-600" />
        <p className="text-slate-500 font-black uppercase tracking-widest text-xs">Securing Escrow Connection...</p>
      </div>
    );
  }

  if (!loan) return <div className="p-20 text-center font-bold text-slate-400 uppercase">Loan not found.</div>;

  const isLocked = loan.status === 'COLLATERAL_LOCKED' || loan.status === 'ACTIVE';
  const hasDeposits = (loan.escrowBalanceSats || 0) > 0;
  
  // REQUIRED BTC CALCULATION
  const btcPrice = btcPriceData?.inr || 0;
  const ltvRatio = (loan.expectedLtvPercent || 50) / 100;
  const requiredBtc = btcPrice > 0 ? (loan.principalAmount / (ltvRatio * btcPrice)) : 0;

  return (
    <div className="max-w-4xl mx-auto py-12 px-6">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-start">
        
        {/* LEFT: Instructions & Status */}
        <div className="lg:col-span-12 mb-4 text-center animate-in fade-in slide-in-from-top-4 duration-700">
          <Badge className="mb-4 bg-indigo-50 text-indigo-600 border-none px-4 py-1.5 rounded-full font-black text-[10px] uppercase tracking-widest shadow-sm">
            Step 3: Fund Your Loan
          </Badge>
          <h1 className="text-4xl font-black text-slate-900 tracking-tight lg:text-5xl">Deposit Collateral</h1>
          <p className="mt-2 text-slate-500 font-medium">Send BTC to your secure, dedicated escrow address.</p>
        </div>

        {/* CENTER COLUMN: ESCROW CARD */}
        <div className="lg:col-span-7 space-y-6">
          <Card className="border-slate-200 shadow-2xl overflow-hidden rounded-[2.5rem] bg-white ring-1 ring-slate-100 transition-all hover:shadow-indigo-100/50">
            <CardHeader className="bg-slate-50 border-b p-8 flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-xl font-black text-slate-900 flex items-center gap-2">
                  <Bitcoin className="h-6 w-6 text-orange-400 fill-orange-400/10" />
                  Your Escrow Wallet
                </CardTitle>
                <p className="text-xs text-slate-400 font-bold uppercase tracking-wider mt-1">Multi-sig Secure (LinkBit Custody)</p>
              </div>
              <Badge variant={isLocked ? "default" : "outline"} className={`px-4 py-1 rounded-full text-[10px] font-black uppercase tracking-widest ${
                isLocked ? 'bg-indigo-600 text-white shadow-lg border-indigo-500' : 'text-slate-400 border-slate-200'
              }`}>
                {isLocked ? 'COLLATERAL LOCKED' : 'AWAITING FUNDING'}
              </Badge>
            </CardHeader>
            <CardContent className="p-8 space-y-8 text-center uppercase">
              
              {/* QR CODE PLACEHOLDER */}
              <div className="relative inline-block group mb-2">
                <div className="absolute -inset-4 bg-orange-100 rounded-[3rem] opacity-20 blur-xl group-hover:opacity-40 transition-opacity"></div>
                <div className="relative bg-white p-6 rounded-[2rem] border-2 border-slate-100 shadow-inner">
                  <QrCode className="h-40 w-40 text-slate-900 opacity-90" strokeWidth={1} />
                </div>
                <div className="absolute -bottom-2 -right-2 bg-indigo-600 text-white p-2.5 rounded-2xl shadow-xl ring-4 ring-white">
                  <Bitcoin className="h-5 w-5" />
                </div>
              </div>

              <div className="space-y-4">
                <div className="space-y-1">
                  <Label className="text-[10px] font-black text-slate-400 uppercase tracking-widest block text-center">BTC Deposit Address</Label>
                  <p className="text-[9px] text-indigo-500 font-black tracking-widest flex items-center justify-center gap-1 mb-2">
                    <ShieldAlert className="h-2.5 w-2.5" />
                    ALWAYS VERIFY THIS ADDRESS BEFORE SENDING BTC
                  </p>
                </div>
                <div className="flex items-center gap-3 p-2 pl-6 bg-slate-900 rounded-3xl shadow-2xl border border-slate-800 transition-all hover:bg-black group">
                  <span className="text-xs font-mono font-bold text-slate-300 truncate tracking-tight py-3">
                    {escrow?.escrow_address || 'Address Generating...'}
                  </span>
                  <Button 
                    variant="ghost" 
                    size="sm" 
                    onClick={handleCopy}
                    className="ml-auto bg-slate-800 hover:bg-slate-700 text-white rounded-2xl px-5 h-11 shrink-0 transition-all active:scale-95 flex items-center gap-2"
                  >
                    {copied ? <CheckCircle2 className="h-4 w-4 text-green-400" /> : <Copy className="h-4 w-4 text-slate-400" />}
                    {copied ? 'COPIED!' : 'COPY'}
                  </Button>
                </div>
              </div>

              <div className="flex flex-col items-center gap-2 pt-4">
                 <div className="flex items-center gap-2 px-6 py-4 bg-red-50 border border-red-100 rounded-3xl w-full">
                    <ShieldAlert className="h-6 w-6 text-red-500 shrink-0" />
                    <p className="text-[10px] text-red-700 font-bold text-left leading-relaxed leading-tight uppercase tracking-tight italic">
                      WARNING: ONLY SEND BITCOIN (BTC) TO THIS ADDRESS. SENDING ANY OTHER ASSET WILL RESULT IN PERMANENT LOSS.
                    </p>
                  </div>
                  <div className="flex items-center gap-2 text-slate-400">
                    <Clock className="h-3 w-3" />
                    <p className="text-[9px] font-bold uppercase tracking-widest italic">~10–30 minutes depending on network load</p>
                  </div>
              </div>
            </CardContent>
          </Card>

          {/* BALANCE DISPLAY */}
          <Card className="border-slate-200 shadow-xl rounded-3xl overflow-hidden bg-slate-50/50 uppercase">
            <div className="grid grid-cols-2 divide-x divide-slate-100">
                <div className="p-7">
                  <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-1.5 flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    Escrow Balance
                  </p>
                  <p className="text-2xl font-black text-slate-900">
                    {(loan.escrowBalanceSats / 100000000).toFixed(8)} BTC
                  </p>
                  {hasDeposits && !isLocked && (
                    <p className="text-[9px] text-amber-600 font-black mt-1 animate-pulse">DEPOSIT DETECTED - PENDING CONFIRMATION</p>
                  )}
                </div>
                <div className="p-7 bg-white">
                  <p className="text-[10px] font-black text-indigo-600 uppercase tracking-widest mb-1.5 flex items-center gap-1">
                    <Info className="h-3 w-3" />
                    REQUIRED COLLATERAL
                  </p>
                  <p className="text-2xl font-black text-slate-900">
                    {requiredBtc.toFixed(4)} BTC
                  </p>
                  <p className="text-[10px] text-slate-400 font-bold mt-1 tracking-tight">
                    (~₹{loan.principalAmount.toLocaleString()} / {ltvRatio * 100}% LTV)
                  </p>
                </div>
            </div>
          </Card>
        </div>

        {/* RIGHT COLUMN: SUBMISSION FORM */}
        <div className="lg:col-span-5 space-y-6">
          <Card className="border-slate-200 shadow-xl rounded-[2.5rem] bg-white overflow-hidden ring-1 ring-slate-100 h-full">
            <CardHeader className="p-8 pb-4">
              <CardTitle className="text-lg font-black text-slate-900 uppercase tracking-wide">Submit Deposit Proof</CardTitle>
              <p className="text-xs text-slate-400 font-bold uppercase tracking-tight">Manual trigger for network verification</p>
            </CardHeader>
            <CardContent className="p-8 space-y-6">
              {loan.role === 'BORROWER' ? (
                <>
                  <form onSubmit={handleSubmit} className="space-y-6">
                    <div className="space-y-2">
                      <Label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Bitcoin Amount (BTC)</Label>
                      <div className="relative">
                        <Bitcoin className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300" />
                        <Input 
                          type="number" 
                          step="0.00000001"
                          placeholder="e.g. 0.045"
                          value={amountBtc}
                          onChange={(e) => setAmountBtc(e.target.value)}
                          disabled={hasSubmitted || isLocked}
                          className="h-14 pl-12 rounded-2xl border-slate-200 bg-slate-50 focus:bg-white focus:ring-indigo-500 transition-all font-bold text-slate-900"
                        />
                      </div>
                    </div>

                    <div className="space-y-2">
                      <Label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Transaction ID (TXID)</Label>
                      <div className="relative">
                        <ExternalLink className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300" />
                        <Input 
                          placeholder="Paste TX Hash (Optional for Mock)"
                          value={txId}
                          onChange={(e) => setTxId(e.target.value)}
                          disabled={hasSubmitted || isLocked}
                          className="h-14 pl-12 rounded-2xl border-slate-200 bg-slate-50 focus:bg-white transition-all text-xs font-mono font-bold"
                        />
                      </div>
                    </div>

                    <Button 
                      type="submit"
                      disabled={!amountBtc || depositMutation.isPending || hasSubmitted || isLocked}
                      className="w-full bg-slate-900 hover:bg-black text-white h-16 rounded-3xl font-black text-base shadow-xl transition-all hover:-translate-y-1 active:scale-95 disabled:opacity-50 disabled:translate-y-0 group"
                    >
                      {depositMutation.isPending ? (
                        <>
                          <Clock className="h-5 w-5 mr-3 animate-spin" />
                          SUBMITTING...
                        </>
                      ) : hasSubmitted ? (
                        <>
                          <CheckCircle2 className="h-5 w-5 mr-3 text-green-400" />
                          SUBMITTED
                        </>
                      ) : (
                        <>
                          SUBMIT DEPOSIT
                          <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
                        </>
                      )}
                    </Button>
                  </form>

                  {hasSubmitted && (
                    <div className="flex items-center gap-4 p-5 bg-indigo-50 border border-indigo-100 rounded-3xl animate-in zoom-in duration-300">
                      <CheckCircle2 className="h-6 w-6 text-indigo-500" />
                      <div>
                        <p className="text-xs font-black text-indigo-900 uppercase tracking-tight">Deposit Detected!</p>
                        <p className="text-[10px] text-indigo-700 font-medium leading-tight mt-0.5 uppercase">Waiting for 1 LinkBit network confirmation...</p>
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <div className="flex flex-col items-center justify-center p-8 bg-slate-50 rounded-3xl border border-slate-100 space-y-4 text-center">
                  <div className="h-16 w-16 bg-white rounded-full shadow-inner flex items-center justify-center">
                    <Clock className="h-8 w-8 text-indigo-600 animate-pulse" />
                  </div>
                  <div>
                    <h3 className="text-sm font-black text-slate-900 uppercase tracking-widest">Lender View</h3>
                    <p className="text-[10px] text-slate-500 font-medium mt-1 leading-relaxed uppercase">
                      Only the Borrower can deposit collateral. You will be notified once the BTC is locked in escrow.
                    </p>
                  </div>
                </div>
              )}

              {/* NETWORK STATUS MOCK */}
              <div className="pt-6 space-y-4 border-t border-slate-100 uppercase">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Network Confirmations</span>
                  <Badge variant="outline" className={`rounded-full text-[10px] px-2 py-0 border-slate-200 ${hasSubmitted || hasDeposits ? 'bg-indigo-50 text-indigo-600' : 'bg-slate-50'}`}>
                    {isLocked ? '3/3' : (hasSubmitted || hasDeposits) ? '1/3' : '0/0'}
                  </Badge>
                </div>
                <div className="h-1.5 w-full bg-slate-100 rounded-full overflow-hidden">
                  <div 
                    className={`h-full bg-indigo-500 transition-all duration-1000 ${isLocked ? 'w-full' : (hasSubmitted || hasDeposits) ? 'w-1/3' : 'w-0'}`}
                  ></div>
                </div>
              </div>
            </CardContent>
            <CardFooter className="p-8 pt-0 mt-auto">
               <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-2xl border border-slate-100 w-full shadow-inner">
                  <Clock className="h-4 w-4 text-slate-400 shrink-0" />
                  <p className="text-[10px] text-slate-500 font-bold leading-tight uppercase tracking-tight">
                    ESTIMATED SETTLEMENT: 10-60 MINS
                  </p>
               </div>
            </CardFooter>
          </Card>
        </div>

        {/* BOTTOM: NAVIGATION */}
        {isLocked && (
          <div className="lg:col-span-12 animate-in slide-in-from-bottom-8 duration-500">
            <Card className="bg-indigo-600 border-none shadow-2xl p-8 rounded-[2.5rem] flex flex-col md:flex-row items-center justify-between gap-6">
              <div className="flex items-center gap-6">
                <div className="bg-indigo-500 p-4 rounded-3xl shadow-inner">
                  <ShieldCheckIcon className="h-10 w-10 text-indigo-100" />
                </div>
                <div>
                  <h3 className="text-2xl font-black text-white tracking-tight leading-tight">Collateral Fully Secured!</h3>
                  <p className="text-indigo-200 font-medium text-sm">Your BTC is now locked in escrow. Fiat disbursement is being prepared.</p>
                </div>
              </div>
              <Button 
                onClick={() => navigate(getLoanRoute(loanId!, loan.status))}
                className="bg-white hover:bg-indigo-50 text-indigo-600 h-16 px-10 rounded-3xl font-black text-base shadow-xl shadow-indigo-900/20 group"
              >
                PROCEED TO DASHBOARD
                <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
              </Button>
            </Card>
          </div>
        )}

      </div>
    </div>
  );
};

// Helper for Icons missing in Lucide standard import but mentioned in plan/UI
const ShieldCheckIcon = (props: any) => (
  <svg
    {...props}
    xmlns="http://www.w3.org/2000/svg"
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10" />
    <path d="m9 12 2 2 4-4" />
  </svg>
)
