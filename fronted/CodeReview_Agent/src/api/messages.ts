import type { ApiResult } from './auth';
import type { ConversationMessage, Page } from '../types';

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

/** 获取某对话的消息列表（分页） */
export async function listMessages(
  conversationId: string,
  pageNum = 1,
  pageSize = 20,
): Promise<ApiResult<Page<ConversationMessage>>> {
  const params = new URLSearchParams({ pageNum: String(pageNum), pageSize: String(pageSize) });
  return fetchApi(`/api/conversations/${conversationId}/messages?${params}`);
}

/** 获取某对话的全部消息（按 messageIndex 升序） */
export async function listAllMessages(
  conversationId: string,
): Promise<ApiResult<ConversationMessage[]>> {
  return fetchApi(`/api/conversations/${conversationId}/messages/all`);
}

/** 获取单条消息详情 */
export async function getMessage(
  conversationId: string,
  id: number,
): Promise<ApiResult<ConversationMessage>> {
  return fetchApi(`/api/conversations/${conversationId}/messages/${id}`);
}
