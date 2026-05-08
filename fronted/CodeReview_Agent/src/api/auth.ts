export interface ApiResult<T = unknown> {
  code: number;
  message: string;
  data: T | null;
}

export interface LoginData {
  tokenInfo: {
    tokenName: string;
    tokenValue: string;
    [key: string]: unknown;
  };
  userId: number;
  username: string;
  nickname: string;
  role: string;
}

export async function login(username: string, password: string): Promise<ApiResult<LoginData>> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  return res.json();
}

export interface RegisterParams {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
}

export async function register(params: RegisterParams): Promise<ApiResult<unknown>> {
  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  return res.json();
}

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem('auth_token');
  if (token) {
    headers['rj-token'] = token;
  }
  return headers;
}

export async function logout(): Promise<ApiResult<unknown>> {
  const res = await fetch('/api/auth/logout', {
    method: 'POST',
    headers: getAuthHeaders(),
  });
  return res.json();
}

export async function getMe(): Promise<ApiResult<LoginData>> {
  const res = await fetch('/api/auth/me', {
    headers: getAuthHeaders(),
  });
  return res.json();
}
