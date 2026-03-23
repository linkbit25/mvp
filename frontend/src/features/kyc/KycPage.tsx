import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import api from '@/services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useQuery } from '@tanstack/react-query';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ShieldCheck, Loader2, AlertCircle, Clock } from 'lucide-react';

export const KycPage = () => {
  const navigate = useNavigate();
  const { user, updateKycStatus } = useAuthStore();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [formData, setFormData] = useState({
    bankAccountNumber: '',
    ifsc: '',
    upiId: ''
  });

  // Continuously poll the server for the latest Auth status in case Admin approves it from the dashboard.
  const { data: latestProfile } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: async () => (await api.get('/auth/me')).data,
    refetchInterval: 5000,
  });

  useEffect(() => {
    // If we fetched a newer status from the polling, update our local store
    if (latestProfile?.kycStatus && latestProfile.kycStatus !== user?.kycStatus) {
      updateKycStatus(latestProfile.kycStatus);
    }
    
    if (user?.kycStatus === 'VERIFIED') {
      navigate('/marketplace');
    }
  }, [user?.kycStatus, latestProfile?.kycStatus, navigate, updateKycStatus]);

  const handleStartVerification = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);
    try {
      await api.post('/auth/kyc/submit', formData);
      updateKycStatus('SUBMITTED');
    } catch (err: any) {
      setError('Verification failed: ' + (err.response?.data?.message || err.message));
    } finally {
      setIsLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  if (user?.kycStatus === 'SUBMITTED') {
    return (
      <div className="min-h-[60vh] flex items-center justify-center p-4">
        <Card className="w-full max-w-md border-slate-200 shadow-xl overflow-hidden hover:border-indigo-200 transition-all duration-300">
          <div className="h-2 bg-amber-500 w-full" />
          <CardHeader className="text-center pt-8 pb-4">
            <div className="mx-auto bg-amber-50 w-20 h-20 rounded-full flex items-center justify-center mb-6 ring-4 ring-amber-50/50">
              <Clock className="h-10 w-10 text-amber-600" />
            </div>
            <CardTitle className="text-2xl font-bold text-slate-900 tracking-tight">Verification Pending</CardTitle>
            <CardDescription className="text-slate-500 mt-2 text-md px-4">
              Your details have been submitted and are currently under review by our administration team. You will be notified once approved.
            </CardDescription>
          </CardHeader>
          <CardContent className="pb-10 pt-4 flex justify-center">
            <Button onClick={() => navigate('/dashboard')} variant="outline" className="w-full">
              Return to Dashboard
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-[60vh] flex items-center justify-center p-4">
      <Card className="w-full max-w-md border-slate-200 shadow-xl overflow-hidden hover:border-indigo-200 transition-all duration-300">
        <div className="h-2 bg-indigo-600 w-full" />
        <CardHeader className="text-center pt-8 pb-4">
          <div className="mx-auto bg-indigo-50 w-20 h-20 rounded-full flex items-center justify-center mb-6 ring-4 ring-indigo-50/50">
            <ShieldCheck className="h-10 w-10 text-indigo-600" />
          </div>
          <CardTitle className="text-2xl font-bold text-slate-900 tracking-tight">Identity Verification</CardTitle>
          <CardDescription className="text-slate-500 mt-2 text-md px-4">
            To ensure a secure lending environment, we require identity verification before you can participate in the marketplace.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6 pb-10 px-8">
          {error && (
            <div className="flex items-center gap-2 p-4 bg-red-50 border border-red-100 rounded-xl text-red-600 text-sm animate-in slide-in-from-top-2 duration-300">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <p>{error}</p>
            </div>
          )}
          
          <form onSubmit={handleStartVerification} className="space-y-6">
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="bankAccountNumber">Bank Account Number</Label>
                <Input
                  id="bankAccountNumber"
                  name="bankAccountNumber"
                  required
                  placeholder="e.g. 1234567890"
                  value={formData.bankAccountNumber}
                  onChange={handleChange}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ifsc">IFSC Code</Label>
                <Input
                  id="ifsc"
                  name="ifsc"
                  required
                  placeholder="e.g. SBIN0001234"
                  value={formData.ifsc}
                  onChange={handleChange}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="upiId">UPI ID</Label>
                <Input
                  id="upiId"
                  name="upiId"
                  required
                  placeholder="e.g. user@upi"
                  value={formData.upiId}
                  onChange={handleChange}
                />
              </div>
            </div>

            <Button 
              type="submit"
              disabled={isLoading}
              className="w-full bg-indigo-600 hover:bg-indigo-700 h-12 rounded-xl text-md font-semibold text-white shadow-lg shadow-indigo-200"
            >
              {isLoading ? (
                <>
                  <Loader2 className="h-5 w-5 animate-spin mr-2" />
                  Submitting Details...
                </>
              ) : (
                'Submit KYC Details'
              )}
            </Button>
          </form>
          
          <p className="text-center text-[11px] text-slate-400 px-6 uppercase tracking-widest font-medium">
            Admin review takes up to 24 hours.
          </p>
        </CardContent>
      </Card>
    </div>
  );
};
