import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/button';
import { LayoutDashboard, ShoppingCart, Landmark, ShieldCheck, LogOut } from 'lucide-react';
import { BTCPriceHeader } from './BTCPriceHeader';
import { NotificationBell } from './NotificationBell';

export const Layout = () => {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <header className="bg-white border-b border-slate-200 sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center space-x-8">
              <Link to="/dashboard" className="text-2xl font-bold text-indigo-600 flex items-center gap-2">
                <Landmark className="h-8 w-8 text-indigo-600" />
                <span>LinkBit</span>
              </Link>
              
              <nav className="hidden md:flex space-x-4">
                <Link to="/dashboard" className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-slate-700 hover:text-indigo-600">
                  <LayoutDashboard className="h-4 w-4" />
                  Dashboard
                </Link>
                <Link to="/marketplace" className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-slate-700 hover:text-indigo-600">
                  <ShoppingCart className="h-4 w-4" />
                  Marketplace
                </Link>
                {user?.role === 'ADMIN' && (
                  <Link to="/admin" className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-slate-700 hover:text-indigo-600">
                    <ShieldCheck className="h-4 w-4" />
                    Admin
                  </Link>
                )}
              </nav>
            </div>
            
            <div className="flex items-center gap-4">
              <NotificationBell />
              <div className="text-right mr-2">
                <p className="text-sm font-medium text-slate-900">{user?.name || 'User'}</p>
                <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold">{user?.role || 'CLIENT'}</p>
              </div>
              <Button variant="ghost" size="icon" onClick={handleLogout} title="Logout">
                <LogOut className="h-5 w-5 text-slate-500" />
              </Button>
            </div>
          </div>
        </div>
      </header>
      
      <BTCPriceHeader />

      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-8">
        <Outlet />
      </main>
      
      <footer className="bg-white border-t border-slate-200 py-6 text-center text-slate-500 text-sm">
        &copy; {new Date().getFullYear()} LinkBit Fintech. All rights reserved.
      </footer>
    </div>
  );
};
