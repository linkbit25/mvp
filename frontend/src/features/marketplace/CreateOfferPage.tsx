import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import api from '@/services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2 } from 'lucide-react';

export const CreateOfferPage = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [formData, setFormData] = useState({
    loanAmountInr: '',
    interestRate: '',
    expectedLtvPercent: '',
    tenureDays: ''
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    setLoading(true);
    setError('');
    
    try {
      // 1. Force fetch the latest KYC status before validating, to ensure we don't block them with a stale cache
      const meRes = await api.get('/auth/me');
      useAuthStore.getState().updateKycStatus(meRes.data.kycStatus);
      
      if (meRes.data.kycStatus !== 'VERIFIED') {
        setError(`KYC Not Verified (Current Status: ${meRes.data.kycStatus}). Redirecting to verification page...`);
        setTimeout(() => {
          navigate('/kyc');
        }, 2000);
        setLoading(false);
        return;
      }
    
      await api.post('/offers', {
        loan_amount_inr: Number(formData.loanAmountInr),
        interest_rate: Number(formData.interestRate),
        expected_ltv_percent: Number(formData.expectedLtvPercent),
        tenure_days: Number(formData.tenureDays)
      });
      navigate('/marketplace');
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create offer');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-xl mx-auto py-8 animate-in fade-in duration-500">
      <Card className="border-slate-200 shadow-sm">
        <CardHeader>
          <CardTitle className="text-2xl text-slate-900">Create Loan Offer</CardTitle>
          <CardDescription>Specify the terms for your loan offer to borrowers</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            {error && (
              <div className="p-3 bg-red-50 text-red-600 rounded-md text-sm border border-red-100">
                {error}
              </div>
            )}
            
            <div className="space-y-2">
              <Label htmlFor="loanAmountInr" className="text-slate-700">Loan Amount (₹)</Label>
              <Input 
                id="loanAmountInr" 
                name="loanAmountInr" 
                type="number" 
                required 
                min="1000"
                placeholder="e.g. 50000"
                value={formData.loanAmountInr}
                onChange={handleChange}
                className="focus-visible:ring-indigo-500"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="interestRate" className="text-slate-700">Interest Rate (%)</Label>
                <Input 
                  id="interestRate" 
                  name="interestRate" 
                  type="number" 
                  step="0.1"
                  required 
                  min="0"
                  max="100"
                  placeholder="e.g. 5.5"
                  value={formData.interestRate}
                  onChange={handleChange}
                  className="focus-visible:ring-indigo-500"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="expectedLtvPercent" className="text-slate-700">Expected LTV (%)</Label>
                <Input 
                  id="expectedLtvPercent" 
                  name="expectedLtvPercent" 
                  type="number" 
                  required 
                  min="1"
                  max="90"
                  placeholder="e.g. 60"
                  value={formData.expectedLtvPercent}
                  onChange={handleChange}
                  className="focus-visible:ring-indigo-500"
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="tenureDays" className="text-slate-700">Tenure (Days)</Label>
              <Input 
                id="tenureDays" 
                name="tenureDays" 
                type="number" 
                required 
                min="1"
                placeholder="e.g. 30"
                value={formData.tenureDays}
                onChange={handleChange}
                className="focus-visible:ring-indigo-500"
              />
            </div>

            <Button type="submit" className="w-full bg-indigo-600 hover:bg-indigo-700 text-white" disabled={loading}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
              {loading ? 'Creating...' : 'Publish Offer'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
};
