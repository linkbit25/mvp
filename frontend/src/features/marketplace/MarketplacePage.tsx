import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import api from '@/services/api';
import { useAuthStore } from '@/store/authStore';
import type { Offer } from './types';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { 
  Building2, 
  Clock, 
  Percent, 
  BarChart3, 
  ArrowRight,
  AlertCircle,
  Loader2,
  Search
} from 'lucide-react';
import { useState } from 'react';

const OfferSkeleton = () => (
  <Card className="border-slate-200 animate-pulse">
    <CardHeader className="space-y-2">
      <div className="h-6 bg-slate-200 rounded w-3/4" />
      <div className="h-4 bg-slate-100 rounded w-1/2" />
    </CardHeader>
    <CardContent className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="h-10 bg-slate-50 rounded" />
        <div className="h-10 bg-slate-50 rounded" />
      </div>
    </CardContent>
    <CardFooter>
      <div className="h-10 bg-slate-200 rounded w-full" />
    </CardFooter>
  </Card>
);

export const MarketplacePage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore(state => state.user);
  const [applyingId, setApplyingId] = useState<string | null>(null);

  const { data: offers, isLoading, error, refetch } = useQuery<Offer[]>({
    queryKey: ['offers'],
    queryFn: async () => {
      const response = await api.get('/offers');
      return response.data;
    },
  });

  const connectMutation = useMutation({
    mutationFn: async (offerId: string) => {
      return api.post('/loans/connect', { offer_id: offerId });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-loans'] });
      navigate('/dashboard');
    },
    onSettled: () => setApplyingId(null)
  });

  const handleApply = (offer: Offer) => {
    if (user?.kycStatus !== 'VERIFIED') {
      navigate('/kyc');
      return;
    }
    setApplyingId(offer.offer_id);
    connectMutation.mutate(offer.offer_id);
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="h-10 bg-slate-200 rounded w-64 animate-pulse" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3, 4, 5, 6].map(i => <OfferSkeleton key={i} />)}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center space-y-4 bg-red-50 border border-red-100 rounded-2xl">
        <AlertCircle className="h-12 w-12 text-red-500" />
        <div>
          <h2 className="text-xl font-bold text-slate-900">Connection Error</h2>
          <p className="text-slate-600">Failed to fetch the latest loan offers.</p>
        </div>
        <Button onClick={() => refetch()} variant="outline" className="mt-4">
          Try Again
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 tracking-tight">Loan Marketplace</h1>
          <p className="text-slate-500 mt-1">Discover peer-to-peer loan offers with BTC collateral</p>
        </div>
        <div className="relative w-full md:w-64">
           <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
           <input 
              type="text" 
              placeholder="Filter offers..." 
              className="w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
           />
        </div>
      </div>

      {offers && offers.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {offers
            .filter(offer => offer.lender_id !== user?.userId)
            .map((offer) => (
            <Card key={offer.offer_id} className="hover:shadow-lg transition-all duration-300 border-slate-200 group flex flex-col">
              <CardHeader className="pb-4">
                <div className="flex justify-between items-start">
                  <div className="p-2 bg-indigo-50 rounded-lg group-hover:bg-indigo-600 group-hover:text-white transition-colors">
                    <Building2 className="h-5 w-5 text-indigo-600 group-hover:text-white" />
                  </div>
                  <Badge variant="secondary" className="bg-slate-100 text-slate-600 font-medium">
                    Peer Offer
                  </Badge>
                </div>
                <CardTitle className="mt-4 text-xl">₹{offer.loan_amount.toLocaleString()}</CardTitle>
                <CardDescription className="flex items-center gap-1.5 mt-1 font-medium text-slate-500">
                  Lender: {offer.lender_pseudonym}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 flex-1">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1">
                    <div className="flex items-center gap-1.5 text-xs text-slate-500 uppercase font-bold tracking-wider">
                      <Percent className="h-3 w-3" />
                      Interest
                    </div>
                    <div className="text-lg font-bold text-emerald-600">{offer.interest_rate}% APR</div>
                  </div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-1.5 text-xs text-slate-500 uppercase font-bold tracking-wider">
                      <Clock className="h-3 w-3" />
                      Tenure
                    </div>
                    <div className="text-lg font-bold text-slate-900">{offer.tenure_days} Days</div>
                  </div>
                </div>
                <div className="pt-4 border-t border-slate-100">
                   <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5 text-xs text-slate-500 font-bold uppercase tracking-wider">
                        <BarChart3 className="h-3 w-3" />
                        Collateral LTV
                      </div>
                      <div className="text-sm font-bold text-slate-900">{offer.expected_ltv}% Target</div>
                   </div>
                   <div className="w-full bg-slate-100 h-1.5 rounded-full mt-2">
                      <div className="bg-indigo-500 h-1.5 rounded-full" style={{ width: `${offer.expected_ltv}%` }} />
                   </div>
                </div>
              </CardContent>
              <CardFooter className="pt-2">
                <Button 
                  onClick={() => handleApply(offer)}
                  disabled={applyingId === offer.offer_id}
                  className="w-full bg-slate-900 hover:bg-black text-white py-6 rounded-xl text-md font-semibold group/btn"
                >
                  {applyingId === offer.offer_id ? (
                    <Loader2 className="h-5 w-5 animate-spin mr-2" />
                  ) : (
                    <>
                      Apply for Loan
                      <ArrowRight className="h-4 w-4 ml-2 group-hover/btn:translate-x-1 transition-transform" />
                    </>
                  )}
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="border-dashed border-slate-300 bg-slate-50/50 py-20">
          <CardHeader className="flex flex-col items-center justify-center text-center space-y-4">
            <div className="bg-slate-200 p-6 rounded-full">
              <Search className="h-10 w-10 text-slate-400" />
            </div>
            <div className="space-y-2">
              <CardTitle className="text-2xl font-bold text-slate-900">No offers available</CardTitle>
              <CardDescription className="max-w-md mx-auto">
                The loan marketplace is currently waiting for new offers. Check back soon or create your own lending profile!
              </CardDescription>
            </div>
          </CardHeader>
        </Card>
      )}
    </div>
  );
};
