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
  Smartphone, 
  CheckCircle2, 
  AlertCircle, 
  Clock, 
  ArrowRight,
  ShieldCheck,
  ExternalLink,
  Info,
  Image as ImageIcon
} from 'lucide-react';
import { useState } from 'react';

export const DisbursementPage = () => {
  const { id: loanId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [txRef, setTxRef] = useState('');
  const [proofUrl, setProofUrl] = useState('');

  // 1. Fetch Loan Details
  const { data: loan, isLoading: isLoanLoading } = useQuery({
    queryKey: ['loan', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/details`);
      return res.data;
    },
    refetchInterval: (data: any) =>
      data?.fiatReceivedConfirmedAt || data?.status === 'DISPUTE_OPEN' || data?.status === 'ACTIVE'
        ? false
        : 5000,
    refetchIntervalInBackground: false,
  });

  // 2. Fetch Payment Details (Lenders only usually)
  const isLender = loan?.role === 'LENDER';

  const { data: paymentDetails, isLoading: isPaymentLoading } = useQuery({
    queryKey: ['paymentDetails', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/payment-details`);
      return res.data;
    },
    enabled: !!loanId && isLender,
  });

  // 3. Mark Disbursed Mutation
  const disburseMutation = useMutation({
    mutationFn: (data: { transaction_reference: string, proof_image_url: string }) => 
      api.post(`/loans/${loanId}/disburse`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
    }
  });

  // 4. Confirm Receipt Mutation
  const confirmMutation = useMutation({
    mutationFn: () => api.post(`/loans/${loanId}/confirm-receipt`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
    }
  });

  // 5. Open Dispute Mutation
  const disputeMutation = useMutation({
    mutationFn: () => api.post(`/loans/${loanId}/open-dispute`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
    }
  });

  if (isLoanLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-24 space-y-4">
        <Clock className="animate-spin h-10 w-10 text-indigo-600" />
        <p className="text-slate-500 font-black uppercase tracking-widest text-xs tracking-tighter">Syncing Financial Channel...</p>
      </div>
    );
  }

  if (!loan) return <div className="p-20 text-center font-bold text-slate-400 uppercase">Loan not found.</div>;

  const isDisbursed = !!loan.fiatDisbursedAt;
  const isReceived = !!loan.fiatReceivedConfirmedAt;
  const isDisputed = loan.status === 'DISPUTE_OPEN';
  const isActive = loan.status === 'ACTIVE';

  return (
    <div className="max-w-4xl mx-auto py-12 px-6">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-start">
        
        {/* HEADER */}
        <div className="lg:col-span-12 mb-4 text-center animate-in fade-in slide-in-from-top-4 duration-700">
          <Badge className="mb-4 bg-indigo-50 text-indigo-600 border-none px-4 py-1.5 rounded-full font-black text-[10px] uppercase tracking-widest shadow-sm">
            Step 4: Disbursement
          </Badge>
          <h1 className="text-4xl font-black text-slate-900 tracking-tight lg:text-5xl">Fiat Disbursement</h1>
          <p className="mt-2 text-slate-500 font-medium italic">Peer-to-peer transfer via Bank or UPI</p>
        </div>

        {/* ACCOUNT DETAILS CARD (Visible to Lender) */}
        <div className="lg:col-span-7 space-y-6">
          <Card className="border-slate-200 shadow-2xl overflow-hidden rounded-[2.5rem] bg-white ring-1 ring-slate-100 uppercase transition-all hover:shadow-indigo-100/50">
            <CardHeader className="bg-slate-50 border-b p-8 flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-xl font-black text-slate-900 flex items-center gap-2">
                  <CreditCard className="h-6 w-6 text-indigo-600" />    
                  Target Account
                </CardTitle>
                <p className="text-xs text-slate-400 font-bold uppercase tracking-wider mt-1">Recipient: {loan.borrower.name}</p>
              </div>
              <Badge variant="outline" className="px-4 py-1 rounded-full text-[10px] font-black uppercase tracking-widest text-slate-400 border-slate-200">
                P2P TRANSFER
              </Badge>
            </CardHeader>
            <CardContent className="p-8 space-y-8">
              {isLender ? (
                isPaymentLoading ? (
                  <div className="animate-pulse space-y-4">
                    <div className="h-12 bg-slate-100 rounded-2xl w-full"></div>
                    <div className="h-12 bg-slate-100 rounded-2xl w-3/4"></div>
                  </div>
                ) : (
                  <div className="space-y-6">
                    <div className="p-8 bg-indigo-600 rounded-[2rem] text-white shadow-xl shadow-indigo-200 relative overflow-hidden group">
                       <div className="absolute top-0 right-0 p-8 opacity-10 group-hover:scale-110 transition-transform">
                          <IndianRupee className="h-24 w-24" />
                       </div>
                       <p className="text-[10px] font-black uppercase tracking-widest opacity-80 mb-1">Transfer Amount</p>
                       <h2 className="text-4xl font-black tracking-tight flex items-baseline gap-2">
                          <span className="text-xl opacity-60">SEND:</span>
                          ₹{loan.principalAmount.toLocaleString()}
                       </h2>
                       <div className="mt-4 flex items-center gap-2 text-[10px] font-black bg-indigo-700/50 w-fit px-3 py-1.5 rounded-full ring-1 ring-white/20 underline-offset-4 decoration-indigo-300">
                          <AlertCircle className="h-3 w-3" />
                          VERIFY RECIPIENT BEFORE SENDING
                       </div>
                    </div>

                    <div className="p-6 bg-slate-50 rounded-3xl border border-slate-100 flex items-center gap-4 transition-all hover:bg-slate-100/50">
                       <div className="bg-white p-3 rounded-2xl shadow-sm">
                          <CreditCard className="h-6 w-6 text-slate-600" />
                       </div>
                       <div>
                          <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest leading-none mb-1">Bank Account / IFSC</p>
                          <p className="text-lg font-black text-slate-900 leading-none">
                            {paymentDetails?.account_number || 'N/A'}
                          </p>
                          <p className="text-xs font-bold text-indigo-600 mt-1">{paymentDetails?.ifsc || ''}</p>
                       </div>
                    </div>

                    <div className="p-6 bg-slate-900 rounded-3xl border border-slate-800 flex items-center gap-4 shadow-xl transition-all hover:bg-black">
                       <div className="bg-slate-800 p-3 rounded-2xl shadow-inner">
                          <Smartphone className="h-6 w-6 text-indigo-400" />
                       </div>
                       <div>
                          <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest leading-none mb-1">UPI ID</p>
                          <p className="text-lg font-black text-white leading-none">
                            {paymentDetails?.upi_id || 'N/A'}
                          </p>
                       </div>
                    </div>
                  </div>
                )
              ) : (
                <div className="p-8 border-2 border-dashed border-slate-100 rounded-3xl flex flex-col items-center justify-center text-center space-y-3">
                  <div className="bg-indigo-50 p-4 rounded-full">
                    <ShieldCheck className="h-8 w-8 text-indigo-600" />
                  </div>
                  <div>
                    <p className="text-slate-900 font-black uppercase tracking-tight">Security Check</p>
                    <p className="text-[11px] text-slate-400 font-bold uppercase tracking-tight max-w-[200px] mx-auto leading-relaxed">
                      Only the lender can view your payment details to initiate transfer.
                    </p>
                  </div>
                </div>
              )}

              <div className="pt-6 border-t border-slate-100 flex items-center gap-4">
                <Info className="h-5 w-5 text-slate-300 shrink-0" />
                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-tight leading-relaxed italic">
                  LinkBit acts as a secure escrow for BTC, but fiat transfers are direct P2P. Verify all details before clicking pay.
                </p>
              </div>
            </CardContent>
          </Card>

          {/* TRANSFER SUMMARY */}
          <Card className="border-slate-200 shadow-xl rounded-3xl overflow-hidden bg-white uppercase transition-all hover:scale-[1.01]">
            <div className="flex items-center justify-between p-7 px-8">
                <div>
                  <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-1">Current Status</p>
                  <div className="flex items-center gap-2">
                    <Badge className={`rounded-full px-4 py-1 text-[10px] font-black uppercase tracking-tighter shadow-sm border-none ${
                      isDisputed ? 'bg-red-50 text-red-600' : 
                      isReceived ? 'bg-green-50 text-green-600' : 
                      isDisbursed ? 'bg-indigo-50 text-indigo-600' : 
                      'bg-slate-50 text-slate-400'
                    }`}>
                      {isDisputed ? 'DISPUTE OPEN' : isReceived ? 'FUNDS RECEIVED' : isDisbursed ? 'PAID BY LENDER' : 'AWAITING TRANSFER'}
                    </Badge>
                    {isDisbursed && !isReceived && (
                      <span className="flex h-2 w-2 rounded-full bg-indigo-500 animate-ping"></span>
                    )}
                  </div>
                </div>
                <div className="text-right">
                   {!isLender && (
                      <>
                        <p className="text-[10px] font-black text-indigo-600 uppercase tracking-widest mb-1">Expected Arrival</p>
                        <p className="text-[10px] font-bold text-slate-400 italic font-mono uppercase">~2-10 Minutes via IMPS/UPI</p>
                      </>
                   )}
                </div>
            </div>
          </Card>
        </div>

        {/* RIGHT COLUMN: ACTIONS */}
        <div className="lg:col-span-5 space-y-6">
          <Card className="border-slate-200 shadow-xl rounded-[2.5rem] bg-white overflow-hidden ring-1 ring-slate-100 h-full flex flex-col">
            <CardHeader className="p-8 pb-4">
              <CardTitle className="text-lg font-black text-slate-900 uppercase tracking-wide">
                {isLender ? 'Mark as Disbursed' : 'Confirm Receipt'}
              </CardTitle>
              <p className="text-xs text-slate-400 font-bold uppercase tracking-tight">
                {isLender ? 'Provide proof after sending fiat' : 'Unlock your loan once funds arrive'}
              </p>
            </CardHeader>
            <CardContent className="p-8 space-y-6 flex-grow">
              
              {isLender ? (
                <div className="space-y-6 h-full flex flex-col">
                   {isDisbursed && !isReceived ? (
                      <div className="p-10 bg-indigo-50 border border-indigo-100 rounded-[2rem] flex flex-col items-center justify-center text-center space-y-4 animate-in zoom-in duration-500">
                         <div className="bg-white p-4 rounded-full shadow-sm">
                            <Clock className="h-10 w-10 text-indigo-600 animate-pulse" />
                         </div>
                         <div>
                            <p className="text-sm font-black text-indigo-900 uppercase">Payment Pending</p>
                            <p className="text-[10px] text-indigo-700 font-bold uppercase tracking-tighter mt-1">Waiting for borrower to confirm receipt ⏳</p>
                         </div>
                      </div>
                   ) : (
                      <>
                        <div className="space-y-2">
                          <Label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Transaction Reference</Label>
                          <div className="relative">
                            <ExternalLink className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300" />
                            <Input 
                              placeholder="Bank UTR or UPI Ref ID"
                              value={txRef}
                              onChange={(e) => setTxRef(e.target.value)}
                              disabled={isDisbursed}
                              className="h-14 pl-12 rounded-2xl border-slate-200 bg-slate-50 focus:bg-white transition-all font-bold text-slate-900"
                            />
                          </div>
                        </div>

                        <div className="space-y-2">
                          <Label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Transfer Proof URL</Label>
                          <div className="relative">
                            <ImageIcon className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300" />
                            <Input 
                              value={proofUrl}
                              onChange={(e) => setProofUrl(e.target.value)}
                              disabled={isDisbursed}
                              className="h-14 pl-12 rounded-2xl border-slate-200 bg-slate-50 focus:bg-white transition-all text-[11px] font-medium text-indigo-600 italic"
                            />
                          </div>
                        </div>

                        <div className="p-4 bg-amber-50 border border-amber-100 rounded-2xl flex items-start gap-3">
                           <AlertCircle className="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
                           <p className="text-[10px] text-amber-700 font-bold uppercase tracking-tight leading-snug">
                             WARNING: ONLY mark as paid AFTER the actual transfer is completed. Incorrect notification will cause disputes ⚠️
                           </p>
                        </div>
                      </>
                   )}

                   <div className="mt-auto pt-6">
                      <Button 
                        disabled={!txRef || disburseMutation.isPending || isDisbursed}
                        onClick={() => disburseMutation.mutate({ transaction_reference: txRef, proof_image_url: proofUrl })}
                        className="w-full bg-slate-900 hover:bg-black text-white h-16 rounded-3xl font-black text-base shadow-xl transition-all active:scale-95 disabled:opacity-50 group uppercase tracking-widest"
                      >
                        {disburseMutation.isPending ? 'PROCESSING...' : isDisbursed ? 'PAYMENT LOGGED' : 'MARK AS PAID'}
                        {!isDisbursed && <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />}
                      </Button>
                    </div>
                </div>
              ) : (
                <div className="space-y-6 h-full flex flex-col">
                   {!isDisbursed ? (
                     <div className="p-12 border-2 border-dashed border-slate-100 rounded-[2rem] flex flex-col items-center justify-center text-center space-y-4">
                        <Clock className="h-12 w-12 text-slate-200 animate-pulse" />
                        <div>
                          <p className="text-slate-400 font-bold uppercase tracking-widest text-[10px]">Lender is initiating transfer...</p>
                          <p className="text-[9px] text-slate-300 font-bold uppercase tracking-tighter mt-2">Funds usually arrive within a few minutes ⚡</p>
                        </div>
                     </div>
                   ) : (
                     <div className="space-y-6 animate-in slide-in-from-right-8 duration-500 uppercase">
                        <div className="p-6 bg-green-50 rounded-3xl border border-green-100 flex items-center gap-4 shadow-sm shadow-green-100/50">
                          <CheckCircle2 className="h-8 w-8 text-green-500 shrink-0" />
                          <div>
                            <p className="text-xs font-black text-green-900 uppercase">TRANSFER COMPLETED BY LENDER</p>
                            <p className="text-[10px] text-green-700 font-bold uppercase tracking-tight mt-0.5">Ref: {loan.disbursementReference}</p>
                          </div>
                        </div>

                        <Button 
                          onClick={() => confirmMutation.mutate()}
                          disabled={confirmMutation.isPending || isReceived || isDisputed}
                          className="w-full bg-indigo-600 hover:bg-indigo-700 text-white h-16 rounded-3xl font-black text-base shadow-xl shadow-indigo-100 transition-all active:scale-95 disabled:opacity-50 uppercase tracking-widest"
                        >
                          {confirmMutation.isPending ? 'CONFIRMING...' : 'CONFIRM RECEIVED'}
                        </Button>

                        <div className="pt-4 border-t border-slate-100 uppercase">
                          <Button 
                            variant="ghost" 
                            onClick={() => disputeMutation.mutate()}
                            disabled={disputeMutation.isPending || isReceived || isDisputed}
                            className="w-full text-red-500 hover:text-red-600 hover:bg-red-50 h-14 rounded-2xl font-black text-[10px] uppercase tracking-widest transition-colors tracking-widest"
                          >
                            {isDisputed ? 'DISPUTE FILED' : 'RAISE DISPUTE'}
                          </Button>
                        </div>
                     </div>
                   )}
                </div>
              )}
            </CardContent>
            <CardFooter className="p-8 pt-0 flex flex-col gap-3">
               <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-2xl border border-slate-100 w-full shadow-inner">
                  <ShieldCheck className="h-4 w-4 text-slate-400 shrink-0" />
                  <p className="text-[10px] text-slate-500 font-black leading-tight uppercase tracking-widest">
                    SECURE PEER-TO-PEER CHANNEL
                  </p>
               </div>
               <p className="text-[9px] text-slate-400 font-bold text-center uppercase tracking-tight px-4 leading-tight italic">
                 Links to your bank transaction proofs will be audited in case of dispute. LinkBit does not hold fiat funds.
               </p>
            </CardFooter>
          </Card>
        </div>

        {/* BOTTOM NAV: SUCCESS BLOCK */}
        {(isActive || isReceived) && (
          <div className="lg:col-span-12 animate-in slide-in-from-bottom-8 duration-500">
             <Card className="bg-green-600 border-none shadow-2xl p-8 rounded-[2.5rem] flex flex-col md:flex-row items-center justify-between gap-6">
              <div className="flex items-center gap-6">
                <div className="bg-green-500 p-4 rounded-3xl shadow-inner shadow-green-700/20">
                  <CheckCircle2 className="h-10 w-10 text-green-100" />
                </div>
                <div className="uppercase">
                  <h3 className="text-2xl font-black text-white tracking-tight leading-none mb-2 underline decoration-green-300 underline-offset-8">Loan Successfully Activated!</h3>
                  <p className="text-green-100 font-bold text-[10px] tracking-widest opacity-80">Fiat Received • Collateral Locked • Term Clock Started</p>
                </div>
              </div>
              <Button 
                onClick={() => navigate(`/loans/${loanId}`)}
                className="bg-white hover:bg-green-50 text-green-600 h-16 px-10 rounded-3xl font-black text-base shadow-xl shadow-green-900/20 group uppercase tracking-widest"
              >
                BACK TO LOAN DETAIL
                <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
              </Button>
            </Card>
          </div>
        )}

        {isDisputed && (
           <div className="lg:col-span-12 animate-in slide-in-from-bottom-8 duration-500">
             <Card className="bg-red-600 border-none shadow-2xl p-8 rounded-[2.5rem] flex flex-col md:flex-row items-center justify-between gap-6">
              <div className="flex items-center gap-6">
                <div className="bg-red-500 p-4 rounded-3xl shadow-inner">
                  <AlertCircle className="h-10 w-10 text-red-100" />
                </div>
                <div>
                  <h3 className="text-2xl font-black text-white tracking-tight leading-tight uppercase">Dispute Under Review</h3>
                  <p className="text-red-200 font-bold text-xs uppercase tracking-tight">Our compliance team is verifying the bank transaction proof.</p>
                </div>
              </div>
              <Button 
                onClick={() => navigate(`/loans/${loanId}`)}
                className="bg-white hover:bg-red-50 text-red-600 h-14 px-8 rounded-2xl font-black text-xs shadow-xl shadow-red-900/20"
              >
                GO TO CHAT
              </Button>
            </Card>
          </div>
        )}

      </div>
    </div>
  );
};
