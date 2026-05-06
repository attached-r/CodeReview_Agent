import type {
  SSEStartEvent,
  SSEAgentEvent,
  SSECompletedEvent,
  SSEErrorEvent,
  SSEEventName,
} from '../types';

export type SSECallback = {
  onStart?: (data: SSEStartEvent) => void;
  onAgentEvent?: (data: SSEAgentEvent) => void;
  onCompleted?: (data: SSECompletedEvent) => void;
  onError?: (data: SSEErrorEvent) => void;
};

/**
 * 提交代码生成任务，通过 EventSource 接收 SSE 流式推送
 * 返回 AbortController 用于取消请求
 */
export function submitCodeTask(
  requirement: string,
  callbacks: SSECallback,
): AbortController {
  const controller = new AbortController();

  fetch('/api/code-task/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ requirement }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        callbacks.onError?.({ message: `请求失败: ${response.status}` });
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        callbacks.onError?.({ message: '响应流不可用' });
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
        callbacks.onError?.({ message: `连接异常: ${err.message}` });
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
      case 'START':
        callbacks.onStart?.(data as SSEStartEvent);
        break;
      case 'AGENT_EVENT':
        callbacks.onAgentEvent?.(data as SSEAgentEvent);
        break;
      case 'COMPLETED':
        callbacks.onCompleted?.(data as SSECompletedEvent);
        break;
      case 'ERROR':
        callbacks.onError?.(data as SSEErrorEvent);
        break;
    }
  } catch {
    // 忽略解析失败的事件
  }
}
