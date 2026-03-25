import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/services/api';
import { useAuthStore } from '@/store/authStore';
import { Card, CardContent, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { 
  Signature, 
  ShieldCheck, 
  Clock, 
  AlertCircle,
  FileText,
  UserCheck,
  Lock,
  ShieldAlert
} from 'lucide-react';

import { getLoanRoute } from './loanRoutes';

export const LoanAgreementPage = () => {
  const { id: loanId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const queryClient = useQueryClient();
  

  const { data: loan, isLoading: isLoadingLoan } = useQuery({
    queryKey: ['loan', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/details`);
      return res.data;
    }
  });

  const { data: agreement, isLoading: isLoadingAgreement } = useQuery({
    queryKey: ['agreement', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/agreement`);
      return res.data;
    },
    refetchInterval: 3000 // Poll for signature updates
  });

  const isLoading = isLoadingLoan || isLoadingAgreement;
  const isLender = loan?.role === 'LENDER';
  const hasSigned = isLender ? !!agreement?.lenderSignature : !!agreement?.borrowerSignature;
  const isFullySigned = !!agreement?.lenderSignature && !!agreement?.borrowerSignature;

  useEffect(() => {
    if (isFullySigned && loan) {
      const timer = setTimeout(() => {
        navigate(getLoanRoute(loanId!, loan.status));
      }, 3000); // 3-second delay to show completion
      return () => clearTimeout(timer);
    }
  }, [isFullySigned, navigate, loanId, loan]);

  const signMutation = useMutation({
    mutationFn: () => {
      if (!window.confirm('By signing this agreement, you legally commit to the terms specified. Continuance will apply your cryptographic signature.')) {
        throw new Error('Cancelled');
      }
      return api.post(`/loans/${loanId}/sign`, { signature_string: `SIGNED_BY_${user?.userId}_${Date.now()}` });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agreement', loanId] });
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
    }
  });

  if (isLoading) return <div className="p-20 text-center"><Clock className="animate-spin h-10 w-10 mx-auto text-indigo-600 mb-4" />Loading Agreement...</div>;
  if (!loan || !agreement) return <div className="p-20 text-center">Agreement data not found.</div>;

  const formatTimestamp = (ts: string) => {
    if (!ts) return '';
    return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true });
  };

  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      {/* LOCKED BANNER */}
      <div className="mb-6 bg-slate-900 text-white px-6 py-4 rounded-2xl flex items-center justify-between shadow-xl ring-1 ring-slate-800">
        <div className="flex items-center gap-3">
          <ShieldAlert className="h-5 w-5 text-indigo-400" />
          <div>
            <p className="text-xs font-black uppercase tracking-widest text-indigo-300">LOCKED CONTRACT</p>
            <p className="text-sm font-medium opacity-90">This agreement is final and cannot be modified.</p>
          </div>
        </div>
        <Lock className="h-5 w-5 opacity-30" />
      </div>

      {/* HEADER SECTION */}
      <div className="mb-8 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <div className="bg-indigo-600 p-3 rounded-2xl shadow-lg ring-1 ring-indigo-500">
            <FileText className="h-6 w-6 text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-black text-slate-900 tracking-tight">Final Agreement</h1>
            <p className="text-sm text-slate-500 font-medium">Reference: {loanId?.slice(0, 8).toUpperCase()}</p>
          </div>
        </div>
        <Badge className={`px-4 py-1.5 rounded-full text-xs font-bold uppercase tracking-widest ${
          isFullySigned ? 'bg-green-100 text-green-700 border-green-200' : 'bg-amber-100 text-amber-700 border-amber-200 shadow-sm'
        }`}>
          {isFullySigned ? 'Execution Enforced' : 'Awaiting Signatures'}
        </Badge>
      </div>

      <div className="grid grid-cols-1 gap-8">
        {/* CONTRACT BODY */}
        <Card className="border-slate-200 shadow-2xl overflow-hidden rounded-3xl">
          <CardHeader className="bg-slate-50 border-b py-6 px-8">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-indigo-600" />
              <CardTitle className="text-lg font-bold text-slate-800 uppercase tracking-wide underline decoration-indigo-200 underline-offset-4">Terms of Execution</CardTitle>
            </div>
          </CardHeader>
          
          <CardContent className="p-8 space-y-10">
            {/* TERMS GRID */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
              <div className="space-y-1">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Principal</p>
                <p className="text-lg font-black text-slate-900">₹{loan.principalAmount.toLocaleString()}</p>
              </div>
              <div className="space-y-1">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Interest Rate</p>
                <p className="text-lg font-black text-indigo-600">{loan.interestRate}% <span className="text-[10px] font-semibold text-slate-400">p.a</span></p>
              </div>
              <div className="space-y-1">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Tenure</p>
                <p className="text-lg font-black text-slate-900">{loan.tenureDays} Days</p>
              </div>
              <div className="space-y-1">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Expected LTV</p>
                <p className="text-lg font-black text-slate-900">{loan.expectedLtvPercent}%</p>
              </div>
              <div className="space-y-1">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">EMI Amount</p>
                <p className="text-lg font-black text-indigo-600">₹{loan.emiAmount?.toLocaleString() || 0}</p>
              </div>
              <div className="space-y-1">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Total Repayment</p>
                <p className="text-lg font-black text-slate-900">₹{loan.totalRepaymentAmount?.toLocaleString() || 0}</p>
              </div>
            </div>

            <div className="border-t border-slate-100 pt-8">
              <h3 className="text-xs font-black text-slate-900 uppercase tracking-widest mb-4">Agreement Integrity</h3>
              <div className="bg-slate-100/50 border border-slate-200 rounded-2xl p-4 font-mono text-[10px] text-slate-500 break-all leading-normal shadow-inner italic">
                <span className="text-slate-800 font-bold mr-2 not-italic">SHA-256:</span>
                {agreement.agreementHash || 'NOT_FINALIZED'}
              </div>
            </div>

            {/* SIGNATURE TRACKER */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 border-t border-slate-100 pt-8">
              {/* BORROWER */}
              <div className={`p-5 rounded-2xl border transition-all duration-500 scale-100 hover:scale-[1.02] ${
                agreement.borrowerSignature ? 'bg-green-50 border-green-200 ring-2 ring-green-100 shadow-sm' : 'bg-slate-50 border-slate-100'
              }`}>
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <UserCheck className={`h-4 w-4 ${agreement.borrowerSignature ? 'text-green-600' : 'text-slate-400'}`} />
                    <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                      {!isLender ? 'You (Borrower)' : 'Other Party (Borrower)'}
                    </span>
                  </div>
                  {agreement.borrowerSignature && <Badge variant="outline" className="text-[10px] bg-white text-green-600 border-green-200">SIGNED</Badge>}
                </div>
                <div className="min-h-[40px] flex flex-col justify-center">
                  {agreement.borrowerSignature ? (
                    <>
                      <p className="text-[10px] font-mono text-green-700 truncate mb-1 opacity-60 italic">{agreement.borrowerSignature}</p>
                      <p className="text-[10px] font-bold text-green-600 uppercase tracking-tighter">
                        Signed at {formatTimestamp(agreement.borrowerSignedAt)}
                      </p>
                    </>
                  ) : (
                    <p className="text-xs italic text-slate-400 flex items-center gap-2">
                      <Clock className="h-3 w-3 animate-pulse" /> Pending signature...
                    </p>
                  )}
                </div>
              </div>

              {/* LENDER */}
              <div className={`p-5 rounded-2xl border transition-all duration-500 scale-100 hover:scale-[1.02] ${
                agreement.lenderSignature ? 'bg-green-50 border-green-200 ring-2 ring-green-100 shadow-sm' : 'bg-slate-50 border-slate-100'
              }`}>
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <UserCheck className={`h-4 w-4 ${agreement.lenderSignature ? 'text-green-600' : 'text-slate-400'}`} />
                    <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                      {isLender ? 'You (Lender)' : 'Other Party (Lender)'}
                    </span>
                  </div>
                  {agreement.lenderSignature && <Badge variant="outline" className="text-[10px] bg-white text-green-600 border-green-200">SIGNED</Badge>}
                </div>
                <div className="min-h-[40px] flex flex-col justify-center">
                  {agreement.lenderSignature ? (
                    <>
                      <p className="text-[10px] font-mono text-green-700 truncate mb-1 opacity-60 italic">{agreement.lenderSignature}</p>
                      <p className="text-[10px] font-bold text-green-600 uppercase tracking-tighter">
                        Signed at {formatTimestamp(agreement.lenderSignedAt)}
                      </p>
                    </>
                  ) : (
                    <p className="text-xs italic text-slate-400 flex items-center gap-2">
                      <Clock className="h-3 w-3 animate-pulse" /> Pending signature...
                    </p>
                  )}
                </div>
              </div>
            </div>
          </CardContent>

          <CardFooter className="bg-slate-50 border-t p-8 flex flex-col gap-4">
            {isFullySigned && (
              <div className="w-full bg-green-600 text-white p-6 rounded-2xl shadow-xl shadow-green-100 flex items-center justify-center gap-3 animate-in fade-in zoom-in duration-500">
                <ShieldCheck className="h-6 w-6 text-green-200" />
                <div className="text-center">
                  <p className="font-black text-lg">CONTRACT FULLY EXECUTED</p>
                  <p className="text-xs text-green-100 font-medium">Redirecting to management dashboard in 3s...</p>
                </div>
              </div>
            )}

            {!hasSigned && !isFullySigned && (
              <Button 
                onClick={() => signMutation.mutate()}
                disabled={signMutation.isPending}
                className="w-full bg-slate-900 hover:bg-black text-white h-16 rounded-2xl font-black text-base shadow-xl shadow-slate-200 transition-all hover:-translate-y-1 active:scale-95"
              >
                <Signature className="h-5 w-5 mr-3 text-indigo-400" />
                LEGALLY SIGN & EXECUTE
              </Button>
            )}
            
            {hasSigned && !isFullySigned && (
              <div className="w-full text-center p-6 bg-white border-2 border-dashed border-green-200 rounded-2xl">
                <div className="flex items-center justify-center gap-2 text-green-700 font-black mb-1">
                  <UserCheck className="h-5 w-5" />
                  YOUR SIGNATURE APPLIED
                </div>
                <p className="text-xs text-slate-500 font-medium tracking-tight uppercase">Awaiting counter-signature to begin loan execution.</p>
              </div>
            )}

            <div className="flex items-center justify-center gap-3 px-6 py-4 bg-white border border-slate-200 shadow-sm rounded-2xl">
              <AlertCircle className="h-4 w-4 text-amber-500 shrink-0" />
              <p className="text-[10px] text-slate-600 font-bold leading-tight">
                LEGAL NOTICE: By applying your digital signature, you verify that you have read and agreed to the finalized loan terms and the cryptographic hash integrity.
              </p>
            </div>
          </CardFooter>
        </Card>
      </div>
    </div>
  );
};
