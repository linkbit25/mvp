import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface User {
  userId: string;
  email: string;
  role: string;
  name: string;
  kycStatus: string;
}

interface AuthState {
  user: User | null;
  token: string | null;
  login: (user: User, token: string) => void;
  logout: () => void;
  updateKycStatus: (status: string) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: localStorage.getItem('token'),
      login: (user, token) => {
        localStorage.setItem('token', token);
        set({ user, token });
      },
      logout: () => {
        localStorage.removeItem('token');
        set({ user: null, token: null });
      },
      updateKycStatus: (status) => {
        set((state) => {
          if (state.user?.kycStatus === status) return state;
          return {
            user: state.user ? { ...state.user, kycStatus: status } : null,
          };
        });
      },
    }),
    {
      name: 'auth-storage',
    }
  )
);
