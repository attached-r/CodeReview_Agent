import { useState } from 'react';
import type { FeatureType, Conversation } from '../types';

interface User {
  userId: number;
  username: string;
  nickname: string;
  role: string;
}

interface Props {
  active: FeatureType;
  onSwitch: (f: FeatureType) => void;
  taskId: string | null;
  status: 'idle' | 'running' | 'completed' | 'error';
  onNewTask: () => void;
  user: User | null;
  onLogout: () => void;
  conversations: Conversation[];
  selectedConvId: string | null;
  onSelectConversation: (convId: string) => void;
  onDeleteConversation: (convId: string) => Promise<void>;
  onRenameConversation: (convId: string, title: string) => Promise<void>;
}

const features: { key: FeatureType; label: string; icon: string; desc: string }[] = [
  { key: 'generate', label: '代码生成', icon: '⚡', desc: 'Agent 自动规划、编码、审查' },
  { key: 'review', label: '代码审查', icon: '🔍', desc: '静态分析、质量评分、修复建议' },
];

function formatTime(dateStr: string): string {
  const d = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin}分钟前`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}小时前`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}天前`;
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

function statusLabel(status: string): string {
  switch (status) {
    case 'ACTIVE': return '进行中';
    case 'COMPLETED': return '已完成';
    case 'FAILED': return '失败';
    default: return status;
  }
}

function statusClass(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'sidebar-status-running';
    case 'COMPLETED': return 'sidebar-status-completed';
    case 'FAILED': return 'sidebar-status-error';
    default: return '';
  }
}

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'var(--accent)',
  COMPLETED: 'var(--success)',
  FAILED: 'var(--error)',
};

export default function LeftSidebar({
  active, onSwitch, taskId, status, onNewTask, user, onLogout,
  conversations, selectedConvId, onSelectConversation, onDeleteConversation, onRenameConversation,
}: Props) {
  const canSwitch = status !== 'running';
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');

  const handleStartRename = (convId: string, currentTitle: string) => {
    setEditingId(convId);
    setEditTitle(currentTitle);
  };

  const handleSaveRename = async (convId: string) => {
    if (editTitle.trim()) {
      await onRenameConversation(convId, editTitle.trim());
    }
    setEditingId(null);
    setEditTitle('');
  };

  const handleKeyDown = (e: React.KeyboardEvent, convId: string) => {
    if (e.key === 'Enter') {
      handleSaveRename(convId);
    } else if (e.key === 'Escape') {
      setEditingId(null);
      setEditTitle('');
    }
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-logo">CR</div>
        <span className="sidebar-title">CodeReview</span>
      </div>

      {user && (
        <div className="sidebar-user">
          <div className="sidebar-user-avatar">{user.nickname?.charAt(0) || user.username.charAt(0)}</div>
          <div className="sidebar-user-info">
            <span className="sidebar-user-name">{user.nickname || user.username}</span>
            <span className="sidebar-user-role">{user.role === 'ADMIN' ? '管理员' : '用户'}</span>
          </div>
          <button className="sidebar-user-logout" onClick={onLogout} title="退出登录">↩</button>
        </div>
      )}

      <nav className="sidebar-nav">
        <span className="sidebar-section-label">功能</span>
        {features.map((f) => (
          <button
            key={f.key}
            className={`sidebar-btn ${active === f.key ? 'sidebar-btn-active' : ''}`}
            onClick={() => canSwitch && onSwitch(f.key)}
            disabled={!canSwitch}
            title={!canSwitch ? '请等待当前任务完成' : f.desc}
          >
            <span className="sidebar-btn-icon">{f.icon}</span>
            <div className="sidebar-btn-text">
              <span className="sidebar-btn-label">{f.label}</span>
              <span className="sidebar-btn-desc">{f.desc}</span>
            </div>
          </button>
        ))}
      </nav>

      {/* ===== 对话历史 ===== */}
      {conversations.length > 0 && (
        <div className="sidebar-conversations">
          <span className="sidebar-section-label" style={{ padding: '0 18px 8px', display: 'block' }}>
            历史对话 ({conversations.length})
          </span>
          <div className="sidebar-conv-list">
            {conversations.map((conv) => (
              <div
                key={conv.conversationId}
                className={`sidebar-conv-item ${selectedConvId === conv.conversationId ? 'sidebar-conv-active' : ''}`}
                onClick={() => onSelectConversation(conv.conversationId)}
              >
                <div className="sidebar-conv-info">
                  {editingId === conv.conversationId ? (
                    <input
                      className="sidebar-conv-edit"
                      value={editTitle}
                      onChange={e => setEditTitle(e.target.value)}
                      onBlur={() => handleSaveRename(conv.conversationId)}
                      onKeyDown={e => handleKeyDown(e, conv.conversationId)}
                      onClick={e => e.stopPropagation()}
                      autoFocus
                    />
                  ) : (
                    <span
                      className="sidebar-conv-title"
                      onDoubleClick={() => handleStartRename(conv.conversationId, conv.title)}
                    >
                      {conv.title}
                    </span>
                  )}
                  <span className="sidebar-conv-meta">
                    <span
                      className="sidebar-conv-status"
                      style={{ color: STATUS_COLORS[conv.status] || 'var(--text-muted)' }}
                    >
                      ●
                    </span>
                    <span>{formatTime(conv.createdAt)}</span>
                  </span>
                </div>
                <button
                  className="sidebar-conv-del"
                  onClick={e => {
                    e.stopPropagation();
                    onDeleteConversation(conv.conversationId);
                  }}
                  title="删除对话"
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {taskId && (
        <div className="sidebar-task">
          <span className="sidebar-section-label">当前任务</span>
          <div className="sidebar-task-id">
            <code>#{taskId}</code>
            <span className={`sidebar-status sidebar-status-${status}`}>
              {status === 'running' ? '执行中' : status === 'completed' ? '已完成' : status === 'error' ? '失败' : ''}
            </span>
          </div>
        </div>
      )}

      {status !== 'idle' && (
        <div className="sidebar-actions">
          <button className="sidebar-new-btn" onClick={onNewTask}>
            + 新建任务
          </button>
        </div>
      )}

      <div className="sidebar-footer">
        <span className="sidebar-version">Phase 1</span>
      </div>
    </aside>
  );
}
