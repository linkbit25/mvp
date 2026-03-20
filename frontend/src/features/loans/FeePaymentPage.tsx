import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/services/api';
import { Card, CardContent, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { 
  Coins, 
  CreditCard, 
  Clock, 
  AlertCircle, 
  CheckCircle2, 
  ShieldCheck,
  ArrowRight,
  Info
} from 'lucide-react';

export const FeePaymentPage = () => {
  const { id: loanId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: loan, isLoading } = useQuery({
    queryKey: ['loan', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/details`);
      return res.data;
    }
  });

  const payMutation = useMutation({
    mutationFn: () => api.post('/payments/fee/pay', { loan_id: loanId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
    }
  });

  if (isLoading) return <div className="p-20 text-center"><Clock className="animate-spin h-10 w-10 mx-auto text-indigo-600 mb-4" />Loading Payment Details...</div>;
  if (!loan) return <div className="p-20 text-center">Loan not found.</div>;

  const pendingFee = loan.pendingFee;
  const isPendingVerification = pendingFee?.status === 'PENDING';
  const isPaid = pendingFee?.status === 'SUCCESS';

  return (
    <div className="max-w-2xl mx-auto py-12 px-4">
      <div className="mb-8 text-center animate-in fade-in slide-in-from-top-4 duration-700">
        <div className="inline-flex items-center justify-center p-3 bg-indigo-100 rounded-2xl mb-4 shadow-inner">
          <Coins className="h-8 w-8 text-indigo-600" />
        </div>
        <h1 className="text-3xl font-black text-slate-900 tracking-tight">Platform Fee</h1>
        <p className="text-slate-500 font-medium">Step 2: Security & Processing</p>
      </div>

      <Card className="border-slate-200 shadow-2xl overflow-hidden rounded-3xl transition-all duration-500 hover:shadow-indigo-100/50">
        <CardHeader className="bg-slate-50 border-b py-6 px-8 flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-lg font-bold text-slate-800 uppercase tracking-wide">Payment Summary</CardTitle>
            <p className="text-xs text-slate-400 font-bold">REF: {loanId?.slice(0, 8).toUpperCase()}</p>
          </div>
          <Badge className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest ${
            isPaid ? 'bg-green-100 text-green-700 border-green-200' : isPendingVerification ? 'bg-amber-100 text-amber-700 border-amber-200' : 'bg-indigo-100 text-indigo-700 border-indigo-200'
          }`}>
            {isPaid ? 'PAID' : isPendingVerification ? 'AWAITING VERIFICATION' : 'UNPAID'}
          </Badge>
        </CardHeader>

        <CardContent className="p-8 space-y-8">
          {/* FEE DISPLAY WITH CALCULATION */}
          <div className="group relative">
            <div className="flex items-baseline justify-between p-7 bg-slate-900 text-white rounded-3xl shadow-xl ring-1 ring-slate-800 transition-all group-hover:bg-black">
              <div>
                <p className="text-[10px] font-black text-indigo-300 uppercase tracking-widest mb-1.5 flex items-center gap-1.5">
                  <Info className="h-3 w-3" />
                  Platform Fee Breakdown
                </p>
                <div className="flex items-baseline gap-2">
                  <p className="text-3xl font-black">₹{pendingFee?.amountInr?.toLocaleString() || '---'}</p>
                  <p className="text-[11px] font-bold text-slate-400 italic">
                    (2% of ₹{loan.principalAmount.toLocaleString()})
                  </p>
                </div>
              </div>
              <CreditCard className="h-8 w-8 text-slate-700 transition-colors group-hover:text-indigo-500" />
            </div>
            {/* TOOLTIP/HINT */}
            <div className="mt-3 flex items-center justify-center gap-2">
              <Clock className="h-3 w-3 text-slate-400" />
              <p className="text-[10px] text-slate-400 font-bold uppercase tracking-tighter italic">Verification usually takes a few minutes</p>
            </div>
          </div>

          <div className="space-y-4">
            <h3 className="text-sm font-black text-slate-900 uppercase tracking-widest">Process Instructions</h3>
            <div className="grid grid-cols-1 gap-3">
              <div className="flex items-start gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-100 transition-all hover:border-indigo-100 hover:bg-white group">
                <div className="h-7 w-7 bg-white border border-slate-200 rounded-full flex items-center justify-center text-[10px] font-black text-slate-400 shrink-0 shadow-sm group-hover:border-indigo-200 group-hover:text-indigo-600">1</div>
                <p className="text-xs text-slate-600 font-medium leading-relaxed">
                  Click the button below to initiate the platform fee payment for this loan.
                </p>
              </div>
              <div className="flex items-start gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-100">
                <div className="h-7 w-7 bg-white border border-slate-200 rounded-full flex items-center justify-center text-[10px] font-black text-slate-400 shrink-0">2</div>
                <p className="text-xs text-slate-600 font-medium leading-relaxed">
                  The amount will be verified by the LinkBit administrative team.
                </p>
              </div>
              <div className="flex items-start gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-100">
                <div className="h-7 w-7 bg-white border border-slate-200 rounded-full flex items-center justify-center text-[10px] font-black text-slate-400 shrink-0">3</div>
                <p className="text-xs text-slate-600 font-medium leading-relaxed">
                  Once verified, your loan status will transition to <span className="font-bold text-indigo-600">AWAITING COLLATERAL</span>.
                </p>
              </div>
            </div>
          </div>

          {/* SUCCESS INDICATOR AFTER CLICK */}
          {payMutation.isSuccess && !isPaid && (
            <div className="flex items-center gap-4 p-4 bg-indigo-600 text-white rounded-2xl shadow-lg ring-4 ring-indigo-50 shadow-indigo-100 animate-in zoom-in duration-300">
              <CheckCircle2 className="h-6 w-6 text-indigo-200" />
              <p className="text-xs font-black uppercase tracking-tight">Payment initiated successfully!</p>
            </div>
          )}

          {isPendingVerification && (
            <div className="flex items-center gap-4 p-5 bg-amber-50 border border-amber-200 rounded-2xl animate-in fade-in slide-in-from-bottom-2 duration-500 ring-4 ring-amber-50/50">
              <Clock className="h-8 w-8 text-amber-500 animate-pulse" />
              <div>
                <p className="text-xs font-black text-amber-900 uppercase tracking-tight">Waiting for Admin Verification</p>
                <p className="text-[10px] text-amber-700 font-medium leading-tight">Your payment has been logged. Our team is verifying the transaction hash. This typically takes 5-15 minutes.</p>
              </div>
            </div>
          )}

          {isPaid && (
            <div className="flex items-center gap-4 p-5 bg-green-50 border border-green-200 rounded-2xl ring-4 ring-green-50">
              <ShieldCheck className="h-8 w-8 text-green-600" />
              <div>
                <p className="text-xs font-black text-green-900 uppercase tracking-tight">Payment Verified</p>
                <p className="text-[10px] text-green-700 font-medium leading-tight text-opacity-80">Platform fee confirmed. You can now proceed to deposit your BTC collateral.</p>
              </div>
            </div>
          )}
        </CardContent>

        <CardFooter className="bg-slate-50 border-t p-8 flex flex-col gap-4">
          {!pendingFee || (!isPendingVerification && !isPaid) ? (
            <Button 
              onClick={() => payMutation.mutate()}
              disabled={payMutation.isPending}
              className="w-full bg-slate-900 hover:bg-black text-white h-16 rounded-3xl font-black text-base shadow-xl shadow-slate-200 transition-all hover:-translate-y-1 active:scale-95 group"
            >
              {payMutation.isPending ? (
                <>
                  <Clock className="h-5 w-5 mr-3 animate-spin" />
                  PROCESSING...
                </>
              ) : (
                <>
                  <CreditCard className="h-5 w-5 mr-3 text-indigo-400" />
                  CONFIRM & PAY FEE
                  <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
                </>
              )}
            </Button>
          ) : isPaid ? (
            <Button 
              onClick={() => navigate(`/loans/${loanId}`)}
              className="w-full bg-indigo-600 hover:bg-indigo-700 text-white h-16 rounded-3xl font-black text-base shadow-xl shadow-indigo-100 group animate-in slide-in-from-bottom-4 duration-500"
            >
              PROCEED TO COLLATERAL
              <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
            </Button>
          ) : (
            <div className="w-full py-4 text-center">
              <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest flex items-center justify-center gap-2">
                <Info className="h-3 w-3" />
                TRANSACTION UNDER REVIEW
              </p>
            </div>
          )}

          <div className="flex items-center justify-center gap-3 px-6 py-4 bg-white border border-slate-200 shadow-sm rounded-2xl">
            <AlertCircle className="h-4 w-4 text-amber-500 shrink-0" />
            <p className="text-[10px] text-slate-500 font-bold leading-tight uppercase tracking-tight">
              LEGAL NOTICE: This is an MVP mock payment. Clicking "Confirm & Pay Fee" simulates a successful transaction initiation for verification.
            </p>
          </div>
        </CardFooter>
      </Card>
    </div>
  );
};
