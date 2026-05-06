import { useEffect, useRef } from 'react';
import type { StreamMessage } from '../types';

interface Props {
  messages: StreamMessage[];
  title?: string;
}

const nodeColors: Record<string, string> = {
  planner: 'log-planner',
  coder: 'log-coder',
  reviewer: 'log-reviewer',
  system: 'log-system',
};

const nodeLabels: Record<string, string> = {
  planner: 'Planner',
  coder: 'Coder',
  reviewer: 'Reviewer',
  system: 'System',
};

export default function StreamLog({ messages, title = '实时输出' }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="stream-log">
      <div className="stream-log-header">
        <span className="section-title">{title}</span>
        <span className="stream-count">{messages.length} 条消息</span>
      </div>
      <div className="stream-log-body">
        {messages.length === 0 ? (
          <div className="stream-empty">
            <span className="stream-empty-icon">~</span>
            <span>等待任务启动...</span>
          </div>
        ) : (
          messages.map((msg) => (
            <div key={msg.id} className={`stream-entry ${nodeColors[msg.node] ?? ''} stream-type-${msg.type}`}>
              <span className="stream-time">
                {new Date(msg.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
              </span>
              <span className="stream-node-tag">{nodeLabels[msg.node] ?? msg.node}</span>
              <span className="stream-message">{msg.message}</span>
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
