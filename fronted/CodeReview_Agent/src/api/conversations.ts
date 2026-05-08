import type { ApiResult } from './auth';
import type { Conversation, Page } from '../types';

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem('auth_token');
  if (token) {
    headers['rj-token'] = token;
  }
  return headers;
}

function handleUnauthorized() {
  localStorage.removeItem('auth_token');
  localStorage.removeItem('auth_user');
  window.dispatchEvent(new CustomEvent('auth:unauthorized'));
}

async function fetchApi<T>(url: string, options?: RequestInit): Promise<ApiResult<T>> {
  const res = await fetch(url, {
    ...options,
    headers: { ...getAuthHeaders(), ...options?.headers },
  });

  if (res.status === 401) {
    handleUnauthorized();
    throw new Error('登录已过期，请重新登录');
  }

  return res.json();
}

export async function listConversations(
  pageNum = 1,
  pageSize = 20,
  status?: string,
): Promise<ApiResult<Page<Conversation>>> {
  const params = new URLSearchParams({ pageNum: String(pageNum), pageSize: String(pageSize) });
  if (status) params.set('status', status);
  return fetchApi(`/api/conversations?${params}`);
}

export async function getConversation(conversationId: string): Promise<ApiResult<Conversation>> {
  return fetchApi(`/api/conversations/${conversationId}`);
}

export async function updateConversationTitle(
  conversationId: string,
  title: string,
): Promise<ApiResult<Conversation>> {
  return fetchApi(`/api/conversations/${conversationId}`, {
    method: 'PUT',
    body: JSON.stringify({ title }),
  });
}

export async function deleteConversation(conversationId: string): Promise<ApiResult<unknown>> {
  return fetchApi(`/api/conversations/${conversationId}`, {
    method: 'DELETE',
  });
}
