import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useMutation } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import api from '@/services/api';
import { Button, buttonVariants } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Calendar } from '@/components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Landmark, Loader2, AlertCircle, CalendarIcon, CheckCircle2 } from 'lucide-react';
import { format } from 'date-fns';
import { cn } from '@/lib/utils';

const registerSchema = z.object({
  name: z.string().min(1, 'Full name is required'),
  email: z.string().email('Please enter a valid email address'),
  dob: z.any().refine((val) => val instanceof Date && !isNaN(val.getTime()), "Date of birth is required"),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  confirmPassword: z.string().min(1, 'Please confirm your password'),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

type RegisterFormValues = z.infer<typeof registerSchema>;

export const RegisterPage = () => {
  const navigate = useNavigate();
  const { token } = useAuthStore();
  const [isSuccess, setIsSuccess] = useState(false);

  // Redirect if already logged in
  useEffect(() => {
    if (token) {
      navigate('/dashboard');
    }
  }, [token, navigate]);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isValid },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    mode: 'onChange',
  });

  const dob = watch('dob');

  const registerMutation = useMutation({
    mutationFn: async (data: RegisterFormValues) => {
      // The backend expects dob in a specific format, we'll send it as ISO string or date
      const payload = {
        name: data.name,
        email: data.email,
        password: data.password,
        dob: format(data.dob, 'yyyy-MM-dd'),
      };
      const response = await api.post('/auth/register', payload);
      return response.data;
    },
    onSuccess: () => {
      setIsSuccess(true);
      setTimeout(() => {
        navigate('/login');
      }, 3000);
    },
  });

  const onSubmit = (data: RegisterFormValues) => {
    registerMutation.mutate(data);
  };

  if (isSuccess) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
        <Card className="w-full max-w-md shadow-lg border-slate-200 text-center p-6">
          <div className="flex flex-col items-center space-y-4">
            <CheckCircle2 className="h-16 w-16 text-emerald-500 animate-in zoom-in duration-300" />
            <CardTitle className="text-2xl font-bold">Registration Successful!</CardTitle>
            <CardDescription className="text-slate-600">
              Your account has been created. Redirecting you to the login page...
            </CardDescription>
            <Link 
              to="/login" 
              className={cn(buttonVariants({ variant: 'default' }), "mt-4 w-full bg-indigo-600")}
            >
              Go to Login Now
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <Card className="w-full max-w-md shadow-lg border-slate-200">
        <CardHeader className="space-y-1 flex flex-col items-center">
          <div className="bg-indigo-600 p-3 rounded-full mb-4">
            <Landmark className="h-8 w-8 text-white" />
          </div>
          <CardTitle className="text-2xl font-bold text-slate-900">Create Account</CardTitle>
          <CardDescription className="text-slate-500">
            Get started with LinkBit
          </CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
            {registerMutation.isError && (
              <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md flex items-center gap-2 text-sm">
                <AlertCircle className="h-4 w-4" />
                <span>
                  {any(registerMutation.error).response?.status === 409 
                    ? 'Email already exists. Try another one.' 
                    : 'Network error. Please check your connection.'}
                </span>
              </div>
            )}
            
            <div className="space-y-2">
              <Label htmlFor="name">Full Name</Label>
              <Input
                id="name"
                placeholder="John Doe"
                {...register('name')}
                className={errors.name ? 'border-red-500' : ''}
              />
              {errors.name && (
                <p className="text-xs text-red-500">{errors.name.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                placeholder="m@example.com"
                {...register('email')}
                className={errors.email ? 'border-red-500' : ''}
              />
              {errors.email && (
                <p className="text-xs text-red-500">{errors.email.message}</p>
              )}
            </div>

            <div className="space-y-2 flex flex-col">
              <Label htmlFor="dob">Date of Birth</Label>
              <Popover>
                <PopoverTrigger
                  type="button"
                  className={cn(
                    buttonVariants({ variant: "outline" }),
                    "w-full justify-start text-left font-normal h-10 border-slate-200",
                    !dob && "text-muted-foreground",
                    errors.dob && "border-red-500"
                  )}
                >
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {dob ? format(dob, "PPP") : <span>Pick a date</span>}
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="single"
                    selected={dob}
                    onSelect={(date) => date && setValue('dob', date, { shouldValidate: true })}
                    initialFocus
                    disabled={(date) => date > new Date() || date < new Date("1900-01-01")}
                  />
                </PopoverContent>
              </Popover>
              {errors.dob && (
                <p className="text-xs text-red-500 mt-1">
                  {typeof errors.dob.message === 'string' ? errors.dob.message : 'Invalid date'}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                {...register('password')}
                className={errors.password ? 'border-red-500' : ''}
              />
              {errors.password && (
                <p className="text-xs text-red-500">{errors.password.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirm Password</Label>
              <Input
                id="confirmPassword"
                type="password"
                {...register('confirmPassword')}
                className={errors.confirmPassword ? 'border-red-500' : ''}
              />
              {errors.confirmPassword && (
                <p className="text-xs text-red-500">{errors.confirmPassword.message}</p>
              )}
            </div>
          </CardContent>
          <CardFooter className="flex flex-col space-y-4">
            <Button
              type="submit"
              className="w-full bg-indigo-600 hover:bg-indigo-700 h-10 transition-all font-semibold"
              disabled={registerMutation.isPending || !isValid}
            >
              {registerMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                'Create Account'
              )}
            </Button>
            <div className="text-center text-sm text-slate-500">
              Already have an account?{' '}
              <Link
                to="/login"
                className="text-indigo-600 hover:underline font-medium"
              >
                Login
              </Link>
            </div>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
};

// Helper for type safety with any
function any(obj: any): any {
  return obj;
}
