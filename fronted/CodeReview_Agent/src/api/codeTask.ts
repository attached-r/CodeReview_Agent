import type {
  SSEPhaseEvent,
  SSEResultEvent,
  SSEErrorEvent,
  SSEEventName,
} from '../types';

export type SSECallback = {
  onPhase?: (data: SSEPhaseEvent) => void;
  onResult?: (data: SSEResultEvent) => void;
  onError?: (data: SSEErrorEvent) => void;
};

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

/**
 * 提交代码生成任务，通过 fetch + ReadableStream 接收 SSE 流式推送
 * 后端以 Flux<ServerSentEvent<StreamEvent>> 输出，event 类型为 phase / result / error
 */
export function submitCodeTask(
  requirement: string,
  callbacks: SSECallback,
): AbortController {
  const controller = new AbortController();

  fetch('/api/code-task/generate', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ requirement }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (response.status === 401) {
        handleUnauthorized();
        callbacks.onError?.({ type: 'error', phase: 'end', content: '登录已过期，请重新登录' });
        return;
      }
      if (!response.ok) {
        callbacks.onError?.({ type: 'error', phase: 'end', content: `请求失败: ${response.status}` });
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        callbacks.onError?.({ type: 'error', phase: 'end', content: '响应流不可用' });
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // 按双换行分割 SSE 事件
        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';

        for (const part of parts) {
          const event = parseSSEEvent(part);
          if (event) {
            const { eventName, data } = event;
            dispatchEvent(eventName, data, callbacks);
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError?.({ type: 'error', phase: 'end', content: `连接异常: ${err.message}` });
      }
    });

  return controller;
}

function parseSSEEvent(
  chunk: string,
): { eventName: SSEEventName; data: string } | null {
  const lines = chunk.split('\n');
  let eventName: SSEEventName | null = null;
  let data = '';

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim() as SSEEventName;
    } else if (line.startsWith('data:')) {
      data = line.slice(5).trim();
    }
  }

  return eventName && data ? { eventName, data } : null;
}

function dispatchEvent(
  eventName: SSEEventName,
  rawData: string,
  callbacks: SSECallback,
) {
  try {
    const data = JSON.parse(rawData);

    switch (eventName) {
      case 'phase':
        callbacks.onPhase?.(data as SSEPhaseEvent);
        break;
      case 'result':
        callbacks.onResult?.(data as SSEResultEvent);
        break;
      case 'error':
        callbacks.onError?.(data as SSEErrorEvent);
        break;
    }
  } catch {
    // 忽略解析失败的事件
  }
}
