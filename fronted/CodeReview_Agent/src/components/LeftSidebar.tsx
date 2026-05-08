import type { FeatureType } from '../types';

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
}

const features: { key: FeatureType; label: string; icon: string; desc: string }[] = [
  { key: 'generate', label: '代码生成', icon: '⚡', desc: 'Agent 自动规划、编码、审查' },
  { key: 'review', label: '代码审查', icon: '🔍', desc: '静态分析、质量评分、修复建议' },
];

export default function LeftSidebar({ active, onSwitch, taskId, status, onNewTask, user, onLogout }: Props) {
  const canSwitch = status !== 'running';

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
