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
import { getLoanRoute } from './loanRoutes';

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

  const myFee = loan.role === 'BORROWER' ? loan.borrowerFee : loan.lenderFee;
  const otherFee = loan.role === 'BORROWER' ? loan.lenderFee : loan.borrowerFee;
  const isMyFeePaid = myFee?.status === 'SUCCESS';
  const isOtherFeePaid = otherFee?.status === 'SUCCESS';
  const isBothPaid = loan.borrowerFee?.status === 'SUCCESS' && loan.lenderFee?.status === 'SUCCESS';
  const isMyFeePending = myFee?.status === 'PENDING';

  return (
    <div className="max-w-3xl mx-auto py-12 px-4">
      <div className="mb-8 text-center animate-in fade-in slide-in-from-top-4 duration-700">
        <div className="inline-flex items-center justify-center p-3 bg-indigo-100 rounded-2xl mb-4 shadow-inner">
          <Coins className="h-8 w-8 text-indigo-600" />
        </div>
        <h1 className="text-3xl font-black text-slate-900 tracking-tight">Platform Fees</h1>
        <p className="text-slate-500 font-medium">Both parties must pay 1% processing fee to proceed</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        {/* MY FEE CARD */}
        <Card className={`border-2 transition-all duration-300 rounded-3xl overflow-hidden ${isMyFeePaid ? 'border-green-200 bg-green-50/30' : 'border-indigo-100 bg-white shadow-xl'}`}>
          <CardHeader className="py-4 px-6 border-b flex flex-row items-center justify-between bg-slate-50/50">
            <CardTitle className="text-sm font-bold uppercase tracking-wider">Your Fee ({loan.role})</CardTitle>
            <Badge className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase ${
              isMyFeePaid ? 'bg-green-100 text-green-700' : isMyFeePending ? 'bg-amber-100 text-amber-700' : 'bg-slate-200 text-slate-500'
            }`}>
              {isMyFeePaid ? 'PAID' : isMyFeePending ? 'PENDING' : 'UNPAID'}
            </Badge>
          </CardHeader>
          <CardContent className="p-6 text-center">
            <p className="text-3xl font-black text-slate-900 mb-1">₹{myFee?.amountInr?.toLocaleString() || (loan.principalAmount * 0.01).toLocaleString()}</p>
            <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">1% of Principal</p>
          </CardContent>
        </Card>

        {/* COUNTERPARTY FEE CARD */}
        <Card className={`border-2 transition-all duration-300 rounded-3xl overflow-hidden ${isOtherFeePaid ? 'border-green-200 bg-green-50/30' : 'border-slate-100 bg-slate-50/30 font-grayscale grayscale contrast-75'}`}>
          <CardHeader className="py-4 px-6 border-b flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-bold uppercase tracking-wider">{loan.role === 'BORROWER' ? 'Lender' : 'Borrower'} Fee</CardTitle>
            <Badge className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase ${
              isOtherFeePaid ? 'bg-green-100 text-green-700' : otherFee?.status === 'PENDING' ? 'bg-amber-100 text-amber-700' : 'bg-slate-200 text-slate-500'
            }`}>
              {isOtherFeePaid ? 'PAID' : otherFee?.status === 'PENDING' ? 'PENDING' : 'UNPAID'}
            </Badge>
          </CardHeader>
          <CardContent className="p-6 text-center">
            <p className="text-3xl font-black text-slate-900 mb-1">₹{otherFee?.amountInr?.toLocaleString() || (loan.principalAmount * 0.01).toLocaleString()}</p>
            <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">1% of Principal</p>
          </CardContent>
        </Card>
      </div>

      <Card className="border-slate-200 shadow-2xl overflow-hidden rounded-3xl transition-all duration-500 hover:shadow-indigo-100/50">
        <CardHeader className="bg-slate-50 border-b py-6 px-8 flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-lg font-bold text-slate-800 uppercase tracking-wide">Execution Status</CardTitle>
            <p className="text-xs text-slate-400 font-bold">REF: {loanId?.slice(0, 8).toUpperCase()}</p>
          </div>
          <Badge className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest ${
            isBothPaid ? 'bg-green-600 text-white' : 'bg-indigo-100 text-indigo-700'
          }`}>
            {isBothPaid ? 'READY FOR COLLATERAL' : 'AWAITING PAYMENTS'}
          </Badge>
        </CardHeader>

        <CardContent className="p-8 space-y-8">
          <div className="space-y-4">
            <h3 className="text-sm font-black text-slate-900 uppercase tracking-widest">Instructions</h3>
            <div className="grid grid-cols-1 gap-3">
              <div className="flex items-start gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-100 transition-all hover:border-indigo-100 hover:bg-white group">
                <div className="h-7 w-7 bg-white border border-slate-200 rounded-full flex items-center justify-center text-[10px] font-black text-slate-400 shrink-0 shadow-sm group-hover:border-indigo-200 group-hover:text-indigo-600">1</div>
                <p className="text-xs text-slate-600 font-medium leading-relaxed">
                  Both Borrower and Lender must independently pay a 1% platform fee.
                </p>
              </div>
              <div className="flex items-start gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-100">
                <div className="h-7 w-7 bg-white border border-slate-200 rounded-full flex items-center justify-center text-[10px] font-black text-slate-400 shrink-0">2</div>
                <p className="text-xs text-slate-600 font-medium leading-relaxed">
                  Payments are verified by the LinkBit team. Status updates in real-time.
                </p>
              </div>
              <div className="flex items-start gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-100">
                <div className="h-7 w-7 bg-white border border-slate-200 rounded-full flex items-center justify-center text-[10px] font-black text-slate-400 shrink-0">3</div>
                <p className="text-xs text-slate-600 font-medium leading-relaxed">
                  Once both fees are <span className="text-green-600 font-black">SUCCESS</span>, the loan moves to <span className="font-bold text-indigo-600 uppercase tracking-tighter italic">AWAITING COLLATERAL</span>.
                </p>
              </div>
            </div>
          </div>

          {/* SUCCESS INDICATOR AFTER CLICK */}
          {payMutation.isSuccess && !isMyFeePaid && (
            <div className="flex items-center gap-4 p-4 bg-indigo-600 text-white rounded-2xl shadow-lg ring-4 ring-indigo-50 shadow-indigo-100 animate-in zoom-in duration-300">
              <CheckCircle2 className="h-6 w-6 text-indigo-200" />
              <p className="text-xs font-black uppercase tracking-tight">Your payment has been initiated!</p>
            </div>
          )}

          {isMyFeePending && (
            <div className="flex items-center gap-4 p-5 bg-amber-50 border border-amber-200 rounded-2xl animate-in fade-in slide-in-from-bottom-2 duration-500 ring-4 ring-amber-50/50">
              <Clock className="h-8 w-8 text-amber-500 animate-pulse" />
              <div>
                <p className="text-xs font-black text-amber-900 uppercase tracking-tight">Your Fee: Awaiting Verification</p>
                <p className="text-[10px] text-amber-700 font-medium leading-tight">Our team is verifying your transaction. This typically takes 5-15 minutes.</p>
              </div>
            </div>
          )}

          {isMyFeePaid && !isBothPaid && (
            <div className="flex items-center gap-4 p-5 bg-indigo-50 border border-indigo-200 rounded-2xl animate-in fade-in duration-500">
              <Clock className="h-8 w-8 text-indigo-600 animate-pulse" />
              <div>
                <p className="text-xs font-black text-indigo-900 uppercase tracking-tight">Waiting for Counterparty</p>
                <p className="text-[10px] text-indigo-700 font-medium leading-tight">Your fee is verified. We are now waiting for the {loan.role === 'BORROWER' ? 'Lender' : 'Borrower'} to complete their payment.</p>
              </div>
            </div>
          )}

          {isBothPaid && (
            <div className="flex items-center gap-4 p-5 bg-green-50 border border-green-200 rounded-2xl ring-4 ring-green-50 animate-in zoom-in duration-500">
              <ShieldCheck className="h-8 w-8 text-green-600" />
              <div>
                <p className="text-xs font-black text-green-900 uppercase tracking-tight">All Fees Verified</p>
                <p className="text-[10px] text-green-700 font-medium leading-tight text-opacity-80">Both parties have paid. Loan is now ready for collateral deposit.</p>
              </div>
            </div>
          )}
        </CardContent>

        <CardFooter className="bg-slate-50 border-t p-8 flex flex-col gap-4">
          {!isMyFeePaid && !isMyFeePending ? (
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
                  PAY YOUR 1% FEE (₹{(loan.principalAmount * 0.01).toLocaleString()})
                  <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
                </>
              )}
            </Button>
          ) : isBothPaid ? (
            loan.role === 'BORROWER' ? (
              <Button 
                onClick={() => navigate(getLoanRoute(loanId!, loan.status))}
                className="w-full bg-indigo-600 hover:bg-indigo-700 text-white h-16 rounded-3xl font-black text-base shadow-xl shadow-indigo-100 group animate-in slide-in-from-bottom-4 duration-500"
              >
                PROCEED TO COLLATERAL
                <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
              </Button>
            ) : (
              <div className="w-full py-4 text-center bg-green-50 rounded-2xl border border-green-100">
                <p className="text-[10px] text-green-700 font-bold uppercase tracking-widest flex items-center justify-center gap-2">
                  <ShieldCheck className="h-4 w-4" />
                  AWAITING BORROWER TO DEPOSIT COLLATERAL
                </p>
              </div>
            )
          ) : (
            <div className="w-full py-4 text-center">
              <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest flex items-center justify-center gap-2">
                <Info className="h-3 w-3" />
                AWAITING FULL FEE COMPLETION
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
