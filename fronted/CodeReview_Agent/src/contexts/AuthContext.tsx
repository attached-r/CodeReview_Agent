import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { login as apiLogin, register as apiRegister } from '../api/auth';
import type { LoginData, RegisterParams } from '../api/auth';

export interface User {
  userId: number;
  username: string;
  nickname: string;
  role: string;
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (params: RegisterParams) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const stored = localStorage.getItem('auth_user');
    const token = localStorage.getItem('auth_token');
    if (stored && token) {
      try {
        setUser(JSON.parse(stored));
      } catch {
        localStorage.removeItem('auth_user');
        localStorage.removeItem('auth_token');
      }
    }
    setLoading(false);

    const onUnauthorized = () => {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
      setUser(null);
    };
    window.addEventListener('auth:unauthorized', onUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', onUnauthorized);
  }, []);

  const login = async (username: string, password: string) => {
    const res = await apiLogin(username, password);
    if (res.code !== 200 || !res.data) {
      throw new Error(res.message || '登录失败');
    }
    const { tokenInfo, ...userData } = res.data;
    localStorage.setItem('auth_token', tokenInfo.tokenValue);
    localStorage.setItem('auth_user', JSON.stringify(userData));
    setUser(userData);
  };

  const register = async (params: RegisterParams) => {
    const res = await apiRegister(params);
    if (res.code !== 200) {
      throw new Error(res.message || '注册失败');
    }
  };

  const logout = () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
