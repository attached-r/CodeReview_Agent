import { useState, type FormEvent } from 'react';
import { useAuth } from '../contexts/AuthContext';
import type { RegisterParams } from '../api/auth';

interface Props {
  onSwitch: () => void;
}

export default function RegisterPage({ onSwitch }: Props) {
  const { register } = useAuth();
  const [form, setForm] = useState<RegisterParams>({
    username: '',
    password: '',
    nickname: '',
    email: '',
  });
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const update = (field: keyof RegisterParams, value: string) =>
    setForm(prev => ({ ...prev, [field]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');

    if (!form.username.trim()) { setError('请输入用户名'); return; }
    if (form.password.length < 6) { setError('密码至少 6 位'); return; }
    if (form.password !== confirmPassword) { setError('两次密码不一致'); return; }

    setSubmitting(true);
    try {
      await register(form);
      onSwitch();
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header">
          <div className="auth-logo">CR</div>
          <h1>创建账号</h1>
          <p className="auth-desc">注册以使用 CodeReview</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          {error && <div className="auth-error">{error}</div>}

          <div className="auth-field">
            <label htmlFor="username">用户名 <span className="auth-required">*</span></label>
            <input
              id="username"
              type="text"
              value={form.username}
              onChange={e => update('username', e.target.value)}
              placeholder="请输入用户名"
              disabled={submitting}
              autoFocus
            />
          </div>

          <div className="auth-field">
            <label htmlFor="nickname">昵称</label>
            <input
              id="nickname"
              type="text"
              value={form.nickname || ''}
              onChange={e => update('nickname', e.target.value)}
              placeholder="选填，默认为用户名"
              disabled={submitting}
            />
          </div>

          <div className="auth-field">
            <label htmlFor="email">邮箱</label>
            <input
              id="email"
              type="email"
              value={form.email || ''}
              onChange={e => update('email', e.target.value)}
              placeholder="选填"
              disabled={submitting}
            />
          </div>

          <div className="auth-field">
            <label htmlFor="password">密码 <span className="auth-required">*</span></label>
            <input
              id="password"
              type="password"
              value={form.password}
              onChange={e => update('password', e.target.value)}
              placeholder="至少 6 位"
              disabled={submitting}
            />
          </div>

          <div className="auth-field">
            <label htmlFor="confirm">确认密码 <span className="auth-required">*</span></label>
            <input
              id="confirm"
              type="password"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              placeholder="再次输入密码"
              disabled={submitting}
            />
          </div>

          <button className="auth-btn" type="submit" disabled={submitting}>
            {submitting ? '注册中...' : '注册'}
          </button>
        </form>

        <div className="auth-footer">
          <span>已有账号？</span>
          <button className="auth-link" onClick={onSwitch}>返回登录</button>
        </div>
      </div>
    </div>
  );
}
