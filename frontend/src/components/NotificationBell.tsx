import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import { Bell, BellRing, Check } from 'lucide-react';
import api from '@/services/api';

export const NotificationBell = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close popover when clicking outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const { data: notifications = [] } = useQuery({
    queryKey: ['notifications'],
    queryFn: async () => (await api.get('/notifications')).data,
    refetchInterval: 30000,
    refetchIntervalInBackground: false,
  });

  const unreadCount = notifications.filter((n: any) => !n.read).length;

  const markAllReadMutation = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        onClick={() => setOpen(v => !v)}
        className="relative p-2 rounded-xl hover:bg-slate-100 transition-colors"
        aria-label="Notifications"
      >
        {unreadCount > 0 ? (
          <BellRing className="h-5 w-5 text-indigo-600" />
        ) : (
          <Bell className="h-5 w-5 text-slate-500" />
        )}
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 h-4 w-4 bg-red-500 rounded-full text-[9px] font-black text-white flex items-center justify-center">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-12 z-50 w-80 bg-white border border-slate-100 rounded-3xl shadow-2xl overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between px-5 py-4 border-b border-slate-50">
            <p className="text-xs font-black text-slate-900 uppercase tracking-widest">Notifications</p>
            {unreadCount > 0 && (
              <button
                onClick={() => markAllReadMutation.mutate()}
                className="flex items-center gap-1 text-[10px] font-black text-indigo-500 uppercase tracking-wide hover:text-indigo-700 transition-colors"
              >
                <Check className="h-3 w-3" />
                Mark all read
              </button>
            )}
          </div>

          {/* List */}
          <div className="max-h-96 overflow-y-auto divide-y divide-slate-50">
            {notifications.length === 0 ? (
              <div className="py-12 text-center">
                <Bell className="h-8 w-8 text-slate-100 mx-auto mb-2" />
                <p className="text-[10px] font-black text-slate-300 uppercase tracking-widest">No notifications yet</p>
              </div>
            ) : (
              notifications.map((n: any) => (
                <div
                  key={n.id}
                  onClick={() => {
                    if (n.loanId) navigate(`/loans/${n.loanId}`);
                    setOpen(false);
                  }}
                  className={`px-5 py-4 cursor-pointer transition-colors hover:bg-slate-50 ${
                    !n.read ? 'bg-indigo-50/40' : ''
                  }`}
                >
                  <div className="flex items-start gap-3">
                    {!n.read && (
                      <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-indigo-500" />
                    )}
                    <div className={!n.read ? '' : 'ml-5'}>
                      <p className="text-xs font-black text-slate-900 leading-tight">{n.title}</p>
                      <p className="text-[10px] text-slate-400 font-medium mt-0.5">{n.message}</p>
                      <p className="text-[9px] text-slate-300 font-medium mt-1 uppercase tracking-widest">
                        {n.createdAt ? format(new Date(n.createdAt), 'MMM dd, HH:mm') : ''}
                      </p>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};
