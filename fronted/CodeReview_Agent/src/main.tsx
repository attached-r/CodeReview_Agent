import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import App from './App.tsx';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import './index.css';

function AppRouter() {
  const { user, loading } = useAuth();
  const [page, setPage] = useState<'login' | 'register'>('login');

  if (loading) {
    return (
      <div className="auth-page">
        <div className="auth-card" style={{ textAlign: 'center', padding: '40px' }}>
          <div className="auth-logo" style={{ margin: '0 auto 16px' }}>CR</div>
          <p style={{ color: 'var(--text-muted)' }}>加载中...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    if (page === 'register') {
      return <RegisterPage onSwitch={() => setPage('login')} />;
    }
    return <LoginPage onSwitch={() => setPage('register')} />;
  }

  return <App />;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <AppRouter />
    </AuthProvider>
  </StrictMode>,
);
