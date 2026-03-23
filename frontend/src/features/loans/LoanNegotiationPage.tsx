import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/services/api';
import { useAuthStore } from '@/store/authStore';
import { Card, CardContent, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { 
  Send, 
  Settings2, 
  MessageSquare, 
  CheckCircle2, 
  XCircle, 
  Clock, 
  AlertCircle,
  ArrowRight
} from 'lucide-react';

interface ChatMessage {
  loan_id: string;
  sender_id: string | null;
  message_text: string;
  timestamp: string;
}

export const LoanNegotiationPage = () => {
  const { id: loanId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const queryClient = useQueryClient();
  const scrollRef = useRef<HTMLDivElement>(null);
  
  const [message, setMessage] = useState('');
  const [showFlash, setShowFlash] = useState(false);
  const [editTerms, setEditTerms] = useState({
    principal_amount: 0,
    interest_rate: 0,
    tenure_days: 0,
    repayment_type: 'BULLET',
    emi_count: 1,
    expected_ltv_percent: 50,
    margin_call_ltv_percent: 70,
    liquidation_ltv_percent: 85
  });

  const { data: loan, isLoading: isLoadingLoan } = useQuery({
    queryKey: ['loan', loanId],
    queryFn: async () => {
      const res = await api.get(`/loans/${loanId}/details`);
      return res.data;
    },
    refetchInterval: (data: any) => data?.status === 'NEGOTIATING' ? 5000 : false,
  });

  const { data: messages = [], isLoading: isLoadingMessages, isRefetching: isPolling } = useQuery({
    queryKey: ['messages', loanId],
    queryFn: async () => {
      const res = await api.get(`/chat/messages`, { params: { loanId } });
      return res.data;
    },
    refetchInterval: loan?.status === 'NEGOTIATING' ? 2500 : false,
  });

  useEffect(() => {
    if (loan) {
      const hasChanged = 
        editTerms.principal_amount !== loan.principalAmount ||
        editTerms.interest_rate !== loan.interestRate ||
        editTerms.tenure_days !== loan.tenureDays;

      if (hasChanged && editTerms.principal_amount !== 0) {
        setShowFlash(true);
        setTimeout(() => setShowFlash(false), 2000);
      }

      setEditTerms({
        principal_amount: loan.principalAmount || 0,
        interest_rate: loan.interestRate || 0,
        tenure_days: loan.tenureDays || 0,
        repayment_type: loan.repaymentType || 'BULLET',
        emi_count: loan.emiCount || 1,
        expected_ltv_percent: loan.expectedLtvPercent || 50,
        margin_call_ltv_percent: loan.marginCallLtvPercent || 70,
        liquidation_ltv_percent: loan.liquidationLtvPercent || 85
      });
    }
  }, [loan]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const sendMessageMutation = useMutation({
    mutationFn: (text: string) => api.post('/chat/send', { loan_id: loanId, message_text: text }),
    onSuccess: () => {
      setMessage('');
      queryClient.invalidateQueries({ queryKey: ['messages', loanId] });
    }
  });

  const updateTermsMutation = useMutation({
    mutationFn: (terms: typeof editTerms) => api.put(`/loans/${loanId}/terms`, terms),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
    }
  });

  const finalizeMutation = useMutation({
    mutationFn: () => {
      return api.post(`/loans/${loanId}/finalize`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loan', loanId] });
    }
  });

  const cancelMutation = useMutation({
    mutationFn: () => {
      return api.post(`/loans/${loanId}/cancel`);
    },
    onSuccess: () => navigate('/dashboard')
  });

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim()) return;
    sendMessageMutation.mutate(message);
  };

  if (isLoadingLoan || isLoadingMessages) return <div className="p-20 text-center"><Clock className="animate-spin h-10 w-10 mx-auto text-indigo-600 mb-4" />Loading Negotiation...</div>;
  if (!loan) return <div className="p-20 text-center">Loan not found.</div>;

  const isNegotiating = loan.status === 'NEGOTIATING';
  const myRole = loan.role;
  const isLender = myRole === 'LENDER';
  const canEdit = isNegotiating && isLender;
  
  const hasIAgreed = isLender ? loan.lenderFinalized : loan.borrowerFinalized;
  const hasOtherAgreed = isLender ? loan.borrowerFinalized : loan.lenderFinalized;

  return (
    <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-6 h-[calc(100vh-10rem)]">
      {/* LEFT: CHAT */}
      <Card className="lg:col-span-7 flex flex-col overflow-hidden border-slate-200 shadow-lg">
        <CardHeader className="border-b bg-slate-50/50 flex flex-row items-center justify-between py-4">
          <div className="flex items-center gap-3">
            <div className="bg-indigo-600 p-2 rounded-lg">
              <MessageSquare className="h-5 w-5 text-white" />
            </div>
            <div>
              <CardTitle className="text-lg">Negotiation Chat</CardTitle>
              <div className="flex items-center gap-2">
                <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold">
                  With {myRole === 'LENDER' ? loan.borrowerPseudonym : loan.lenderPseudonym}
                </p>
                {isPolling && <span className="flex h-1.5 w-1.5 rounded-full bg-green-500 animate-pulse" title="Counterparty active" />}
              </div>
            </div>
          </div>
          <Badge variant={isNegotiating ? 'outline' : 'secondary'} className={isNegotiating ? 'text-green-600 bg-green-50 border-green-200' : ''}>
            {isNegotiating ? 'LIVE CHANNEL' : 'READ ONLY'}
          </Badge>
        </CardHeader>
        
        <CardContent ref={scrollRef} className="flex-1 overflow-y-auto p-6 space-y-4 bg-slate-50/20">
          {messages.map((msg: ChatMessage, idx: number) => {
            const isMe = msg.sender_id === user?.userId;
            const isSystem = !msg.sender_id;
            
            if (isSystem) return (
              <div key={idx} className="flex justify-center my-4">
                <div className="bg-indigo-50 border border-indigo-100 text-indigo-600 text-[11px] px-3 py-1 rounded-full font-medium uppercase tracking-wider">
                  {msg.message_text}
                </div>
              </div>
            );

            return (
              <div key={idx} className={`flex ${isMe ? 'justify-end' : 'justify-start'} group mb-4`}>
                <div className={`max-w-[80%] rounded-2xl px-4 py-3 shadow-sm relative ${
                  isMe ? 'bg-indigo-600 text-white rounded-tr-none' : 'bg-white border text-slate-700 rounded-tl-none'
                }`}>
                  <p className="text-sm leading-relaxed">{msg.message_text}</p>
                  <p className={`text-[10px] mt-1.5 opacity-60 font-medium ${isMe ? 'text-indigo-100' : 'text-slate-400'}`}>
                    {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true })}
                  </p>
                </div>
              </div>
            );
          })}
        </CardContent>

        <CardFooter className="border-t p-4 bg-white flex flex-col gap-2">
          {isPolling && (
            <div className="flex items-center gap-2 text-[10px] text-slate-400 font-medium ml-1">
              <span className="flex h-1.5 w-1.5 rounded-full bg-green-400/50" />
              Counterparty is active
            </div>
          )}
          <form onSubmit={handleSendMessage} className="flex w-full gap-2">
            <Input
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder={isNegotiating ? "Type your counter offer or message..." : "Chat is closed"}
              disabled={!isNegotiating || sendMessageMutation.isPending}
              className="flex-1 h-12 bg-slate-50 border-slate-200 rounded-xl focus-visible:ring-indigo-600"
            />
            <Button 
              type="submit" 
              disabled={!isNegotiating || !message.trim() || sendMessageMutation.isPending}
              className="bg-indigo-600 hover:bg-indigo-700 h-12 w-12 rounded-xl transition-all active:scale-95"
            >
              <Send className="h-5 w-5" />
            </Button>
          </form>
        </CardFooter>
      </Card>

      {/* RIGHT: TERMS & ACTIONS */}
      <Card className="lg:col-span-5 flex flex-col overflow-hidden border-slate-200 shadow-lg">
        <CardHeader className="border-b bg-slate-50/50 py-4">
          <div className="flex items-center gap-3">
            <div className="bg-slate-800 p-2 rounded-lg">
              <Settings2 className="h-5 w-5 text-white" />
            </div>
          </div>
          <div className="flex gap-2">
            <Badge variant={loan.lenderFinalized ? 'default' : 'outline'} className={loan.lenderFinalized ? 'bg-green-600' : 'text-slate-400'}>
              Lender: {loan.lenderFinalized ? 'Agreed' : 'Pending'}
            </Badge>
            <Badge variant={loan.borrowerFinalized ? 'default' : 'outline'} className={loan.borrowerFinalized ? 'bg-green-600' : 'text-slate-400'}>
              Borrower: {loan.borrowerFinalized ? 'Agreed' : 'Pending'}
            </Badge>
          </div>
        </CardHeader>

        <CardContent className="flex-1 overflow-y-auto p-6 space-y-6">
          <div className="grid grid-cols-1 gap-6">
            <div className="space-y-2">
              <label className="text-xs font-bold text-slate-500 uppercase tracking-widest">Principal Amount (₹)</label>
              <Input
                type="number"
                value={editTerms.principal_amount}
                onChange={(e) => setEditTerms({ ...editTerms, principal_amount: Number(e.target.value) })}
                disabled={!canEdit}
                className={`h-11 font-semibold text-lg border-slate-200 bg-white transition-all duration-500 ${
                  showFlash ? 'ring-2 ring-indigo-500 bg-indigo-50/50' : ''
                }`}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-xs font-bold text-slate-500 uppercase tracking-widest">Interest Rate (%)</label>
                <Input
                  type="number"
                  step="0.1"
                  value={editTerms.interest_rate}
                  onChange={(e) => setEditTerms({ ...editTerms, interest_rate: Number(e.target.value) })}
                  disabled={!canEdit}
                  className={`h-11 font-semibold transition-all duration-500 ${
                    showFlash ? 'ring-2 ring-indigo-500 bg-indigo-50/50' : ''
                  }`}
                />
              </div>
              <div className="space-y-2">
                <label className="text-xs font-bold text-slate-500 uppercase tracking-widest">Tenure (Days)</label>
                <Input
                  type="number"
                  value={editTerms.tenure_days}
                  onChange={(e) => setEditTerms({ ...editTerms, tenure_days: Number(e.target.value) })}
                  disabled={!canEdit}
                  className={`h-11 font-semibold transition-all duration-500 ${
                    showFlash ? 'ring-2 ring-indigo-500 bg-indigo-50/50' : ''
                  }`}
                />
              </div>
            </div>

            <div className="border-t border-slate-100 my-2" />

            <div className="space-y-4">
              <div className="flex justify-between items-center bg-indigo-50/30 p-3 rounded-xl border border-indigo-50">
                <span className="text-sm font-medium text-slate-600">Expected LTV</span>
                <span className="text-sm font-bold text-indigo-700">{editTerms.expected_ltv_percent}%</span>
              </div>
              <div className="flex justify-between items-center bg-amber-50/30 p-3 rounded-xl border border-amber-50">
                <span className="text-sm font-medium text-slate-600">Margin Call LTV</span>
                <span className="text-sm font-bold text-amber-700">{editTerms.margin_call_ltv_percent}%</span>
              </div>
              <div className="flex justify-between items-center bg-red-50/30 p-3 rounded-xl border border-red-100">
                <span className="text-sm font-medium text-slate-600">Liquidation LTV</span>
                <span className="text-sm font-bold text-red-700">{editTerms.liquidation_ltv_percent}%</span>
              </div>
            </div>
          </div>
        </CardContent>

        <CardFooter className="border-t p-6 bg-slate-50/50 flex flex-col gap-3">
          {isNegotiating ? (
            <>
              {isLender && (
                <Button 
                  onClick={() => updateTermsMutation.mutate(editTerms)}
                  disabled={updateTermsMutation.isPending}
                  className="w-full bg-slate-900 border border-slate-800 h-11 text-sm font-bold hover:bg-slate-800 transition-all rounded-xl shadow-lg shadow-slate-200"
                >
                  Propose New Terms
                </Button>
              )}
              <div className="grid grid-cols-2 gap-3 w-full">
                <Button 
                  onClick={() => finalizeMutation.mutate()}
                  disabled={finalizeMutation.isPending || hasIAgreed}
                  className={`${hasIAgreed ? 'bg-green-600' : 'bg-indigo-600'} hover:bg-indigo-700 text-sm font-bold h-11 rounded-xl shadow-lg shadow-indigo-100 flex-1 transition-all`}
                >
                  <CheckCircle2 className="h-4 w-4 mr-2" />
                  {hasIAgreed ? (hasOtherAgreed ? 'Finalized' : 'Agreed') : 'Finalize'}
                </Button>
                <Button 
                  variant="outline"
                  onClick={() => cancelMutation.mutate()}
                  className="text-red-600 border-red-200 hover:bg-red-50 text-sm font-bold h-11 rounded-xl"
                >
                  <XCircle className="h-4 w-4 mr-2" />
                  Cancel
                </Button>
              </div>
            </>
          ) : (
            <div className="w-full py-4 text-center">
              <div className="flex items-center justify-center gap-2 text-indigo-700 font-bold mb-3 animate-pulse">
                <CheckCircle2 className="h-5 w-5" />
                <span>CONTRACT FINALIZED</span>
              </div>
              <Button 
                onClick={() => navigate(`/loans/${loanId}`)}
                className="w-full bg-indigo-600 hover:bg-indigo-700 h-11 rounded-xl group"
              >
                Go to Execution Phase
                <ArrowRight className="h-4 w-4 ml-2 group-hover:translate-x-1 transition-transform" />
              </Button>
            </div>
          )}
          
          <div className="flex items-center gap-2 p-3 bg-white/50 border rounded-xl">
            <AlertCircle className="h-4 w-4 text-slate-400" />
            <p className="text-[10px] text-slate-500 font-medium">Both parties must finalize terms before the contract proceeds to signing.</p>
          </div>
        </CardFooter>
      </Card>
    </div>
  );
};
