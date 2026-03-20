import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import api from '@/services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ShieldCheck, Loader2, AlertCircle } from 'lucide-react';

export const KycPage = () => {
  const navigate = useNavigate();
  const { user, updateKycStatus } = useAuthStore();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user?.kycStatus === 'VERIFIED') {
      navigate('/marketplace');
    }
  }, [user, navigate]);

  const handleStartVerification = async () => {
    setIsLoading(true);
    setError(null);
    try {
      await api.post('/auth/kyc/submit');
      updateKycStatus('VERIFIED');
      navigate('/marketplace');
    } catch (err) {
      setError('Verification failed. Please try again later.');
    } finally {
      setIsLoading(false);
    }
  };

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
          
          <div className="space-y-4">
            <div className="flex items-start gap-4 p-3 rounded-lg hover:bg-slate-50 transition-colors">
              <div className="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-500 shrink-0">1</div>
              <p className="text-sm text-slate-600 py-0.5">Automated identity check via our secure partner.</p>
            </div>
            <div className="flex items-start gap-4 p-3 rounded-lg hover:bg-slate-50 transition-colors">
              <div className="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-500 shrink-0">2</div>
              <p className="text-sm text-slate-600 py-0.5">Confirmation usually takes less than 30 seconds.</p>
            </div>
          </div>

          <Button 
            onClick={handleStartVerification}
            disabled={isLoading}
            className="w-full bg-indigo-600 hover:bg-indigo-700 h-12 rounded-xl text-md font-semibold text-white shadow-lg shadow-indigo-200"
          >
            {isLoading ? (
              <>
                <Loader2 className="h-5 w-5 animate-spin mr-2" />
                Processing Identity...
              </>
            ) : (
              'Start Verification'
            )}
          </Button>
          
          <p className="text-center text-[11px] text-slate-400 px-6 uppercase tracking-widest font-medium">
            LinkBit uses bank-grade encryption to protect your data
          </p>
        </CardContent>
      </Card>
    </div>
  );
};
