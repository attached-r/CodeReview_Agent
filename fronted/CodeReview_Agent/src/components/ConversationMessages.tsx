import type { ConversationMessage } from '../types';
import MarkdownRenderer from './MarkdownRenderer';

interface Props {
  messages: ConversationMessage[];
  loading: boolean;
  onClose: () => void;
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin}分钟前`;
  return d.toLocaleString('zh-CN', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function roleLabel(role: string): string {
  switch (role) {
    case 'user': return '用户';
    case 'assistant': return 'AI 助手';
    case 'system': return '系统';
    default: return role;
  }
}

function roleClass(role: string): string {
  switch (role) {
    case 'user': return 'msg-user';
    case 'assistant': return 'msg-assistant';
    case 'system': return 'msg-system';
    default: return '';
  }
}

export default function ConversationMessages({ messages, loading, onClose }: Props) {
  if (loading) {
    return (
      <div className="conv-messages">
        <div className="conv-msg-loading">加载中...</div>
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className="conv-messages">
        <div className="conv-msg-header">
          <h2>对话详情</h2>
          <button className="conv-msg-close" onClick={onClose}>×</button>
        </div>
        <div className="conv-msg-empty">暂无消息记录</div>
      </div>
    );
  }

  return (
    <div className="conv-messages">
      <div className="conv-msg-header">
        <h2>对话详情</h2>
        <span className="conv-msg-count">{messages.length} 条消息</span>
        <button className="conv-msg-close" onClick={onClose}>×</button>
      </div>
      <div className="conv-msg-list">
        {messages.map((msg) => (
          <div key={msg.id} className={`conv-msg-item ${roleClass(msg.role)}`}>
            <div className="conv-msg-meta">
              <span className="conv-msg-role">{roleLabel(msg.role)}</span>
              {msg.modelName && <span className="conv-msg-model">{msg.modelName}</span>}
              <span className="conv-msg-time">{formatTime(msg.createdAt)}</span>
            </div>
            <div className="conv-msg-content">
              <MarkdownRenderer content={msg.content} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
