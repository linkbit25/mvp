import { useQuery } from '@tanstack/react-query';
import api from '@/services/api';
import { useAuthStore } from '@/store/authStore';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Loader2, User, Mail, Phone, ShieldCheck, Building, UserCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';

export const ProfilePage = () => {
  const { user: authUser } = useAuthStore();
  
  const { data: profile, isLoading, error } = useQuery({
    queryKey: ['profile'],
    queryFn: async () => {
      const response = await api.get('/auth/me');
      return response.data;
    }
  });

  if (isLoading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="p-6 text-center text-red-600 bg-red-50 rounded-xl border border-red-200">
        Failed to load profile details. Please try again.
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto py-8 space-y-8 animate-in fade-in duration-500">
      <div className="flex items-center gap-4">
        <div className="p-4 bg-indigo-100 rounded-full">
          <UserCircle className="h-10 w-10 text-indigo-600" />
        </div>
        <div>
          <h1 className="text-3xl font-bold text-slate-900">{profile.pseudonym}</h1>
          <p className="text-slate-500 font-medium capitalize mt-1 border border-slate-200 px-2 py-0.5 rounded-md inline-block text-xs bg-white shadow-sm">
            {authUser?.role || 'User'}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card className="border-slate-200 shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg flex items-center gap-2">
              <User className="h-5 w-5 text-indigo-500" />
              Personal Details
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold mb-1">Email</p>
              <div className="flex items-center gap-2 text-slate-900 font-medium">
                <Mail className="h-4 w-4 text-slate-400" />
                {profile.email}
              </div>
            </div>
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold mb-1">Phone Number</p>
              <div className="flex items-center gap-2 text-slate-900 font-medium">
                <Phone className="h-4 w-4 text-slate-400" />
                {profile.phoneNumber || 'Not provided'}
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200 shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg flex items-center gap-2 flex-1 justify-between">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-5 w-5 text-indigo-500" />
                KYC Status
              </div>
              <Badge variant={profile.kycStatus === 'VERIFIED' ? 'success' : profile.kycStatus === 'REJECTED' ? 'destructive' : 'warning'}>
                {profile.kycStatus}
              </Badge>
            </CardTitle>
            <CardDescription>
              Your identity and banking verification status.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {profile.bankDetails ? (
              <>
                <div>
                  <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold mb-1">Bank Account</p>
                  <div className="flex items-center gap-2 text-slate-900 font-mono">
                    <Building className="h-4 w-4 text-slate-400" />
                    {profile.bankDetails.bankAccountNumber.slice(0, 4)}••••••••••
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold mb-1">IFSC Code</p>
                    <p className="text-slate-900 font-mono">{profile.bankDetails.ifsc}</p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold mb-1">UPI ID</p>
                    <p className="text-slate-900 font-mono">{profile.bankDetails.upiId}</p>
                  </div>
                </div>
              </>
            ) : (
              <p className="text-sm text-slate-500 italic border border-dashed border-slate-200 rounded-lg p-4 bg-slate-50 text-center">
                No bank details provided yet.
              </p>
            )}
            
            {(profile.kycStatus === 'PENDING' || profile.kycStatus === 'REJECTED') && (
              <div className="pt-4 mt-2 border-t border-slate-100">
                <Button 
                  onClick={() => window.location.href = '/kyc'} 
                  className="w-full bg-indigo-600 hover:bg-indigo-700 text-white"
                >
                  <ShieldCheck className="h-4 w-4 mr-2" />
                  {profile.kycStatus === 'REJECTED' ? 'Resubmit Verification' : 'Complete Verification'}
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
