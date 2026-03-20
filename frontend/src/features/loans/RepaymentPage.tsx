import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/services/api';
import { Card, CardContent, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { 
  IndianRupee,
  CreditCard, 
  CheckCircle2, 
  Clock, 
  ArrowUpRight,
  ShieldCheck,
  ChevronRight,
  TrendingUp,
  TrendingDown,
  History,
  FileSearch,
  Receipt,
  Image as ImageIcon
} from 'lucide-react';
import { useState } from 'react';
import { format } from 'date-fns';

export const RepaymentPage = () => {
  const { id: loanId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [repayAmount, setRepayAmount] = useState('');
  const [txRef, setTxRef] = useState('');
  const [proofUrl, setProofUrl] = useState('');
  const [activeTab, setActiveTab] = useState('submissions');

  // 1. Fetch Loan Details (Balances & Submissions)
  const { data: loan, isLoading: isLoanLoading } = useQuery({
    queryKey: ['loan', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/details`);
      return res.data;
    },
    refetchInterval: 5000,
  });

  // 2. Fetch Ledger
  const { data: ledger, isLoading: isLedgerLoading } = useQuery({
    queryKey: ['ledger', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/ledger`);
      return res.data;
    },
  });

  // 3. Repayment Mutation
  const repayMutation = useMutation({
    mutationFn: (data: { amount: number, transaction_reference: string, proof_image_url: string }) => 
      api.post(`/loans/${loanId}/repay`, data),
    onSuccess: () => {
      setRepayAmount('');
      setTxRef('');
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
      queryClient.invalidateQueries({ queryKey: ['ledger', loanId] });
    }
  });

  if (isLoanLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-24 space-y-4">
        <Clock className="animate-spin h-10 w-10 text-indigo-600" />
        <p className="text-slate-500 font-bold uppercase tracking-widest text-[10px]">Syncing Financial Records...</p>
      </div>
    );
  }

  if (!loan) return <div className="p-20 text-center font-bold text-slate-400 uppercase">Loan not found.</div>;

  const totalRepaidAmount = (loan.totalRepaymentAmount - loan.totalOutstanding);
  const progressPercent = Math.min(100, Math.round((totalRepaidAmount / loan.totalRepaymentAmount) * 100));
  const isFullyRepaid = loan.totalOutstanding <= 0;

  return (
    <div className="max-w-5xl mx-auto py-12 px-6">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        
        {/* HEADER & BALANCE HUD */}
        <div className="lg:col-span-12 space-y-8 animate-in fade-in slide-in-from-top-4 duration-700">
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
            <div>
              <div className="flex items-center gap-3 mb-4">
                <Badge variant="outline" className="bg-indigo-50 text-indigo-600 border-none px-4 py-1 rounded-full font-black text-[10px] uppercase tracking-widest">
                  Loan Ref: {loanId?.slice(0, 8)}
                </Badge>
                <Badge className={`rounded-full px-4 py-1 text-[10px] font-black uppercase ${
                  isFullyRepaid ? 'bg-green-100 text-green-700' : 'bg-blue-100 text-blue-700'
                }`}>
                  {isFullyRepaid ? 'FULLY REPAID' : 'ACTIVE LOAN'}
                </Badge>
              </div>
              <h1 className="text-4xl font-black text-slate-900 tracking-tight lg:text-5xl uppercase">Loan Repayment</h1>
              <p className="mt-2 text-slate-500 font-medium italic">Settle your outstanding balance to release collateral</p>
            </div>
            
            <div className="bg-white p-6 rounded-[2rem] shadow-xl border border-slate-100 min-w-[280px]">
              <div className="flex items-center justify-between mb-2">
                <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Repayment Progress</p>
                <p className="text-xs font-black text-indigo-600 uppercase">{progressPercent}%</p>
              </div>
              <div className="h-3 bg-slate-100 rounded-full overflow-hidden">
                <div 
                  className="h-full bg-indigo-600 transition-all duration-500 ease-out"
                  style={{ width: `${progressPercent}%` }}
                />
              </div>
              <div className="mt-4 flex items-center justify-between text-[10px] font-bold text-slate-400 uppercase">
                <span>Repaid: ₹{totalRepaidAmount.toLocaleString()}</span>
                <span>Total: ₹{loan.totalRepaymentAmount.toLocaleString()}</span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
             <Card className="bg-slate-900 text-white rounded-[2rem] border-none shadow-2xl relative overflow-hidden group">
                <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:scale-110 transition-transform">
                  <TrendingDown className="h-16 w-16" />
                </div>
                <CardHeader className="p-8 pb-2">
                  <CardTitle className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Total Outstanding</CardTitle>
                </CardHeader>
                <CardContent className="p-8 pt-0">
                  <p className="text-4xl font-black tracking-tight">₹{loan.totalOutstanding.toLocaleString()}</p>
                </CardContent>
             </Card>

             <Card className="bg-white rounded-[2rem] shadow-xl border-slate-100 uppercase transition-all hover:scale-[1.02]">
                <CardHeader className="p-8 pb-2">
                  <CardTitle className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Principal</CardTitle>
                </CardHeader>
                <CardContent className="p-8 pt-0">
                  <p className="text-3xl font-black text-slate-900 tracking-tight">₹{loan.principalOutstanding.toLocaleString()}</p>
                </CardContent>
             </Card>

             <Card className="bg-white rounded-[2rem] shadow-xl border-slate-100 uppercase transition-all hover:scale-[1.02]">
                <CardHeader className="p-8 pb-2">
                  <CardTitle className="text-[10px] font-black text-indigo-400 uppercase tracking-widest">Interest accrued</CardTitle>
                </CardHeader>
                <CardContent className="p-8 pt-0">
                  <p className="text-3xl font-black text-indigo-600 tracking-tight">₹{loan.interestOutstanding.toLocaleString()}</p>
                </CardContent>
             </Card>
          </div>
        </div>

        {/* REPAYMENT FORM */}
        <div className="lg:col-span-5 space-y-6">
          <Card className="border-slate-200 shadow-2xl rounded-[2.5rem] bg-white overflow-hidden ring-1 ring-slate-100 h-full flex flex-col uppercase">
            <CardHeader className="bg-slate-50 border-b p-8">
              <CardTitle className="text-lg font-black text-slate-900 flex items-center gap-2 tracking-wide uppercase">
                <IndianRupee className="h-6 w-6 text-indigo-600" />    
                Submit Repayment
              </CardTitle>
              <p className="text-xs text-slate-400 font-bold uppercase tracking-tight mt-1">Funds will be verified by the system admin</p>
            </CardHeader>
            <CardContent className="p-8 space-y-6 flex-grow">
              <div className="space-y-6">
                <div className="space-y-2">
                  <Label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Repayment Amount (INR)</Label>
                  <div className="relative">
                    <IndianRupee className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300" />
                    <Input 
                      type="number"
                      placeholder="Enter amount to pay"
                      value={repayAmount}
                      onChange={(e) => setRepayAmount(e.target.value)}
                      disabled={isFullyRepaid}
                      className="h-14 pl-12 rounded-2xl border-slate-200 bg-slate-50 focus:bg-white transition-all font-black text-lg text-slate-900"
                    />
                  </div>
                  {Number(repayAmount) > loan.totalOutstanding && (
                    <p className="text-[10px] font-bold text-red-500 uppercase ml-1">Cannot exceed ₹{loan.totalOutstanding.toLocaleString()}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Transaction Reference</Label>
                  <div className="relative">
                    <Receipt className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300" />
                    <Input 
                      placeholder="Bank UTR or UPI Ref ID"
                      value={txRef}
                      onChange={(e) => setTxRef(e.target.value)}
                      disabled={isFullyRepaid}
                      className="h-14 pl-12 rounded-2xl border-slate-200 bg-slate-50 focus:bg-white transition-all font-bold text-slate-900"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Proof Image URL</Label>
                  <div className="relative">
                    <ImageIcon className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300" />
                    <Input 
                      value={proofUrl}
                      onChange={(e) => setProofUrl(e.target.value)}
                      disabled={isFullyRepaid}
                      className="h-14 pl-12 rounded-2xl border-slate-200 bg-slate-50 focus:bg-white transition-all text-xs font-medium text-indigo-600 italic"
                    />
                  </div>
                </div>
              </div>

              <div className="mt-8">
                <Button 
                  disabled={!repayAmount || !txRef || Number(repayAmount) > loan.totalOutstanding || repayMutation.isPending || isFullyRepaid}
                  onClick={() => repayMutation.mutate({ 
                    amount: Number(repayAmount), 
                    transaction_reference: txRef, 
                    proof_image_url: proofUrl 
                  })}
                  className="w-full bg-slate-900 hover:bg-black text-white h-16 rounded-3xl font-black text-base shadow-xl transition-all active:scale-95 disabled:opacity-50 group uppercase tracking-widest"
                >
                  {repayMutation.isPending ? 'PROCESSING...' : isFullyRepaid ? 'LOAN SETTLED' : 'SUBMIT REPAYMENT'}
                  {!isFullyRepaid && <ArrowUpRight className="h-5 w-5 ml-2 group-hover:translate-x-1 group-hover:-translate-y-1 transition-transform" />}
                </Button>
              </div>
            </CardContent>
            <CardFooter className="p-8 pt-0 flex flex-col gap-4">
               <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-2xl border border-slate-100 w-full shadow-inner">
                  <ShieldCheck className="h-4 w-4 text-slate-400 shrink-0" />
                  <p className="text-[10px] text-slate-500 font-black leading-tight uppercase tracking-widest">
                    Your collateral is safely locked in escrow until full repayment.
                  </p>
               </div>
            </CardFooter>
          </Card>
        </div>

        {/* HISTORY TABS (Submissions & Ledger) */}
        <div className="lg:col-span-7 space-y-6">
            <Card className="border-slate-200 shadow-xl rounded-[2rem] overflow-hidden bg-white uppercase">
              <CardHeader className="p-0">
                <div className="w-full h-16 rounded-none bg-slate-50 p-2 gap-2 flex">
                  <button 
                    onClick={() => setActiveTab('submissions')}
                    className={`flex-1 rounded-2xl font-black text-[10px] uppercase tracking-widest transition-all h-full flex items-center justify-center ${
                      activeTab === 'submissions' ? 'bg-white shadow-sm text-slate-900' : 'text-slate-400'
                    }`}
                  >
                    <History className="h-4 w-4 mr-2" />
                    Submissions
                  </button>
                  <button 
                    onClick={() => setActiveTab('ledger')}
                    className={`flex-1 rounded-2xl font-black text-[10px] uppercase tracking-widest transition-all h-full flex items-center justify-center ${
                      activeTab === 'ledger' ? 'bg-white shadow-sm text-slate-900' : 'text-slate-400'
                    }`}
                  >
                    <FileSearch className="h-4 w-4 mr-2" />
                    Ledger
                  </button>
                </div>
              </CardHeader>
              <CardContent className="p-0 min-h-[400px]">
                {activeTab === 'submissions' ? (
                  <div className="m-0 p-8 space-y-4">
                    {loan.pendingRepayments?.length > 0 ? (
                      <div className="space-y-4">
                        {loan.pendingRepayments.map((rep: any) => (
                          <div key={rep.repaymentId} className="flex items-center justify-between p-6 bg-slate-50 rounded-3xl border border-slate-100 hover:bg-white transition-all group shadow-sm">
                            <div className="flex items-center gap-4">
                              <div className={`p-3 rounded-2xl shadow-inner ${
                                rep.status === 'VERIFIED' ? 'bg-green-100 text-green-600' :
                                rep.status === 'REJECTED' ? 'bg-red-100 text-red-600' :
                                'bg-amber-100 text-amber-600'
                              }`}>
                                  <CreditCard className="h-5 w-5" />
                              </div>
                              <div>
                                <p className="text-sm font-black text-slate-900 tracking-tight">₹{rep.amountInr.toLocaleString()}</p>
                                <p className="text-[10px] text-slate-400 font-bold uppercase">{rep.transactionReference}</p>
                              </div>
                            </div>
                            <div className="text-right">
                                <Badge className={`rounded-full px-4 py-1 text-[10px] font-black uppercase ${
                                  rep.status === 'VERIFIED' ? 'bg-green-100 text-green-700 border-none' :
                                  rep.status === 'REJECTED' ? 'bg-red-100 text-red-700 border-none' :
                                  'bg-amber-100 text-amber-700 border-none animate-pulse'
                                }`}>
                                  {rep.status}
                                </Badge>
                                <p className="text-[9px] text-slate-300 font-bold mt-1 uppercase mt-2">{format(new Date(rep.createdAt), 'MMM dd, HH:mm')}</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="flex flex-col items-center justify-center p-12 text-center space-y-4">
                        <History className="h-10 w-10 text-slate-100" />
                        <p className="text-[10px] font-black text-slate-300 uppercase tracking-widest">No repayment history yet</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="m-0 p-0">
                    <div className="overflow-hidden">
                        <table className="w-full text-left border-collapse">
                          <thead className="bg-slate-50 border-b border-slate-100">
                            <tr>
                              <th className="px-8 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Event Type</th>
                              <th className="px-8 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Time</th>
                              <th className="px-8 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">Amount</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-50">
                            {[...(ledger ?? [])].reverse().map((entry: any, i: number) => (
                              <tr key={i} className="hover:bg-slate-50/50 transition-colors group">
                                <td className="px-8 py-6">
                                    <div className="flex items-center gap-3">
                                      <div className={`p-2 rounded-xl ${entry.type === 'BORROWER_REPAYMENT' ? 'bg-green-50 text-green-600' : 'bg-indigo-50 text-indigo-600'}`}>
                                        {entry.type === 'BORROWER_REPAYMENT' ? <TrendingUp className="h-4 w-4" /> : <TrendingDown className="h-4 w-4" />}
                                      </div>
                                      <span className="text-xs font-black text-slate-900 uppercase tracking-tight">{entry.type}</span>
                                    </div>
                                </td>
                                <td className="px-8 py-6">
                                  <p className="text-[10px] text-slate-400 font-bold uppercase">
                                    {entry.createdAt ? format(new Date(entry.createdAt), 'MMM dd, HH:mm') : '—'}
                                  </p>
                                </td>
                                <td className={`px-8 py-6 text-right font-black text-sm tracking-tight ${entry.type === 'BORROWER_REPAYMENT' ? 'text-green-600' : 'text-slate-900'}`}>
                                    {entry.type === 'BORROWER_REPAYMENT' ? '-' : '+'} ₹{entry.amount.toLocaleString()}
                                </td>
                              </tr>
                            ))}
                            {isLedgerLoading && (
                              <tr>
                                <td colSpan={2} className="px-8 py-12 text-center animate-pulse text-[10px] font-black text-slate-300 uppercase italic">Parsing ledger entries...</td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

            <Card className="bg-indigo-600 border-none shadow-2xl p-8 rounded-[2rem] flex flex-col md:flex-row items-center justify-between gap-6 overflow-hidden relative">
                <div className="absolute -right-8 -bottom-8 opacity-10 rotate-12">
                  <ShieldCheck className="h-40 w-40 text-white" />
                </div>
                <div className="flex items-center gap-5 z-10">
                  <div className="bg-indigo-500 p-4 rounded-3xl shadow-inner">
                    <CheckCircle2 className="h-8 w-8 text-indigo-100" />
                  </div>
                  <div>
                    <h3 className="text-xl font-black text-white tracking-tight leading-none mb-1 uppercase tracking-widest">Collateral Safe</h3>
                    <p className="text-indigo-100 font-bold text-[10px] tracking-tight uppercase opacity-80 italic">Verified in Escrow • multisig protection active</p>
                  </div>
                </div>
                <Button 
                  variant="outline"
                  onClick={() => navigate(`/loans/${loanId}`)}
                  className="bg-transparent hover:bg-white/10 text-white border-white/20 h-14 px-8 rounded-2xl font-black text-[10px] shadow-xl group uppercase tracking-widest z-10"
                >
                  VIEW LOAN DETAILS
                  <ChevronRight className="h-4 w-4 ml-2 group-hover:translate-x-1 transition-transform" />
                </Button>
            </Card>
        </div>

      </div>
    </div>
  );
};
