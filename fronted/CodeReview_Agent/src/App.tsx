import { useState, useRef, useCallback } from 'react';
import type {
  AgentNode, AgentStep, PlannerOutput, CoderOutput, ReviewOutput, TaskStatus,
  FeatureType, StreamMessage, CodeReviewReport,
} from './types';
import { submitCodeTask } from './api/codeTask';
import LeftSidebar from './components/LeftSidebar';
import RequirementInput from './components/RequirementInput';
import AgentFlow from './components/AgentFlow';
import StreamLog from './components/StreamLog';
import CodeReview from './components/CodeReview';
import PlanResult from './components/PlanResult';
import CodeResult from './components/CodeResult';
import ReviewResult from './components/ReviewResult';
import './App.css';

function toAgentNode(node: string): AgentNode {
  const lower = node.toLowerCase();
  if (lower.includes('planner')) return 'planner';
  if (lower.includes('coder')) return 'coder';
  if (lower.includes('reviewer') || lower.includes('review')) return 'reviewer';
  if (lower.includes('accepted') || lower.includes('failed') || lower.includes('end')) return 'end';
  return 'planner';
}

let msgCounter = 0;
function nextMsgId(): string {
  return `msg-${++msgCounter}`;
}

export default function App() {
  const [feature, setFeature] = useState<FeatureType>('generate');

  /* ---- Generate state ---- */
  const [genStatus, setGenStatus] = useState<TaskStatus>('idle');
  const [taskId, setTaskId] = useState<string | null>(null);
  const [steps, setSteps] = useState<AgentStep[]>([]);
  const [genMessages, setGenMessages] = useState<StreamMessage[]>([]);
  const [planResult, setPlanResult] = useState<PlannerOutput | null>(null);
  const [codeResult, setCodeResult] = useState<CoderOutput | null>(null);
  const [reviewResult, setReviewResult] = useState<ReviewOutput | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  /* ---- Review state ---- */
  const [reviewStatus, setReviewStatus] = useState<'idle' | 'running' | 'completed'>('idle');
  const [reviewMessages, setReviewMessages] = useState<StreamMessage[]>([]);
  const [reviewReport, setReviewReport] = useState<CodeReviewReport | null>(null);

  /* ---- Generate handlers ---- */
  const addGenMsg = useCallback((node: AgentNode | 'system', label: string, message: string, type: StreamMessage['type']) => {
    setGenMessages(prev => [...prev, { id: nextMsgId(), node, label, message, timestamp: Date.now(), type }]);
  }, []);

  const addStep = useCallback((node: AgentNode, message: string) => {
    setSteps(prev => [...prev, { node, message, timestamp: Date.now() }]);
  }, []);

  const handleGenSubmit = useCallback((requirement: string) => {
    setGenStatus('running');
    setTaskId(null);
    setSteps([]);
    setGenMessages([]);
    setPlanResult(null);
    setCodeResult(null);
    setReviewResult(null);
    setErrorMessage(null);

    const abort = submitCodeTask(requirement, {
      onStart: (data) => {
        setTaskId(data.taskId);
        addStep('planner', data.message);
        addGenMsg('system', 'System', `任务已创建: ${data.taskId}`, 'info');
        addGenMsg('planner', 'Planner', data.message, 'step');
      },
      onAgentEvent: (data) => {
        const node = toAgentNode(data.node);
        addStep(node, data.message);
        addGenMsg(node, data.node, data.message, 'step');
      },
      onCompleted: (data) => {
        if (data.plan) setPlanResult(data.plan);
        if (data.code) setCodeResult(data.code);
        if (data.review) setReviewResult(data.review);
        addGenMsg('system', 'System', '任务执行完成', 'success');
        setGenStatus('completed');
      },
      onError: (data) => {
        setErrorMessage(data.message);
        addGenMsg('system', 'System', `错误: ${data.message}`, 'error');
        setGenStatus('error');
      },
    });

    abortRef.current = abort;
  }, [addStep, addGenMsg]);

  const handleNewTask = useCallback(() => {
    if (abortRef.current) abortRef.current.abort();
    setGenStatus('idle');
    setTaskId(null);
    setSteps([]);
    setGenMessages([]);
    setPlanResult(null);
    setCodeResult(null);
    setReviewResult(null);
    setErrorMessage(null);
    setReviewStatus('idle');
    setReviewMessages([]);
    setReviewReport(null);
  }, []);

  const handleFeatureSwitch = useCallback((f: FeatureType) => {
    if (genStatus === 'running') return;
    setFeature(f);
  }, [genStatus]);

  /* ---- Review callbacks ---- */
  const onReviewStart = useCallback(() => {
    setReviewStatus('running');
    setReviewMessages([]);
    setReviewReport(null);
  }, []);

  const onReviewMessage = useCallback((msg: StreamMessage) => {
    setReviewMessages(prev => [...prev, msg]);
  }, []);

  const onReviewComplete = useCallback((report: CodeReviewReport) => {
    setReviewReport(report);
    setReviewStatus('completed');
  }, []);

  const genRunning = genStatus === 'running';
  const reviewRunning = reviewStatus === 'running';
  const showRightPanel = feature === 'generate'
    ? (planResult || codeResult || reviewResult || errorMessage)
    : (reviewReport !== null);

  return (
    <div className="app">
      <LeftSidebar
        active={feature}
        onSwitch={handleFeatureSwitch}
        taskId={taskId}
        status={genStatus}
        onNewTask={handleNewTask}
      />

      <main className="center-panel">
        {/* ===== 代码生成 ===== */}
        {feature === 'generate' && (
          <>
            <div className="center-scroll">
              <div className="center-section">
                <RequirementInput onSubmit={handleGenSubmit} disabled={genRunning} />
              </div>

              {(genStatus !== 'idle') && (
                <>
                  <div className="center-section">
                    <AgentFlow steps={steps} status={genStatus} />
                  </div>
                  <div className="center-section">
                    <StreamLog messages={genMessages} title="执行日志" />
                  </div>
                </>
              )}
            </div>
          </>
        )}

        {/* ===== 代码审查 ===== */}
        {feature === 'review' && (
          <div className="center-scroll">
            <CodeReview
              onStart={onReviewStart}
              onMessage={onReviewMessage}
              onComplete={onReviewComplete}
              messages={reviewMessages}
              running={reviewRunning}
            />
          </div>
        )}
      </main>

      <aside className="right-panel">
        {/* ===== 生成结果 ===== */}
        {feature === 'generate' && (
          <div className="right-scroll">
            {errorMessage && (
              <div className="error-box">
                <h3>❌ 执行出错</h3>
                <p>{errorMessage}</p>
              </div>
            )}

            {planResult && (
              <div className="right-section">
                <PlanResult plan={planResult} />
              </div>
            )}
            {codeResult && (
              <div className="right-section">
                <CodeResult code={codeResult} />
              </div>
            )}
            {reviewResult && (
              <div className="right-section">
                <ReviewResult review={reviewResult} />
              </div>
            )}

            {!showRightPanel && genStatus === 'idle' && (
              <div className="right-placeholder">
                <div className="placeholder-icon">→</div>
                <h3>等待任务</h3>
                <p>在左侧输入需求描述并提交，<br />结果将在此处展示</p>
              </div>
            )}
          </div>
        )}

        {/* ===== 审查结果 ===== */}
        {feature === 'review' && (
          <div className="right-scroll">
            {reviewReport && (
              <div className="right-section">
                <ReviewResult
                  review={{
                    accepted: reviewReport.score >= 60,
                    issues: reviewReport.issues.map(i => `[L${i.line}] ${i.message}`),
                    hallucinationIssues: reviewReport.securityIssues,
                    fixSuggestions: reviewReport.suggestions,
                    score: reviewReport.score,
                    summary: reviewReport.summary,
                  }}
                />
              </div>
            )}

            {!reviewReport && reviewStatus === 'idle' && (
              <div className="right-placeholder">
                <div className="placeholder-icon">←</div>
                <h3>等待审查</h3>
                <p>在中间区域粘贴代码并提交，<br />审查结果将在此处展示</p>
              </div>
            )}
          </div>
        )}
      </aside>
    </div>
  );
}
