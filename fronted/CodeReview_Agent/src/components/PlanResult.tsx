import type { PlannerOutput } from '../types';

interface Props {
  plan: PlannerOutput;
}

export default function PlanResult({ plan }: Props) {
  return (
    <div className="result-section">
      <h3 className="section-title">📋 任务规划</h3>

      <div className="result-card">
        <h4>需求分析</h4>
        <p className="result-text">{plan.taskAnalysis}</p>
      </div>

      <div className="result-card">
        <h4>技术栈</h4>
        <p className="result-text">{plan.techStack}</p>
      </div>

      {plan.subTasks && plan.subTasks.length > 0 && (
        <div className="result-card">
          <h4>子任务列表 ({plan.subTasks.length})</h4>
          <div className="subtask-list">
            {plan.subTasks.map((t, i) => (
              <div key={i} className="subtask-item">
                <span className="subtask-num">#{i + 1}</span>
                <div className="subtask-body">
                  <strong>{t.name}</strong>
                  <p>{t.description}</p>
                  {t.targetFile && (
                    <code className="subtask-file">{t.targetFile}</code>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {plan.riskWarning && (
        <div className="result-card result-card-warn">
          <h4>⚠️ 风险警告</h4>
          <p className="result-text">{plan.riskWarning}</p>
        </div>
      )}
    </div>
  );
}
