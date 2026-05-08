import { useState, useRef, useCallback, useEffect } from 'react';
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
import MarkdownRenderer from './components/MarkdownRenderer';
import './App.css';

function phaseToNode(phase: string): AgentNode {
  if (phase === 'planner') return 'planner';
  if (phase === 'coder') return 'coder';
  if (phase === 'reviewer') return 'reviewer';
  return 'end';
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
  const [streamingContent, setStreamingContent] = useState<Record<string, string>>({});
  const abortRef = useRef<AbortController | null>(null);

  /* ---- Right panel resize ---- */
  const [rightWidth, setRightWidth] = useState(420);
  const dragRef = useRef(false);

  useEffect(() => {
    const onMouseMove = (e: MouseEvent) => {
      if (!dragRef.current) return;
      const newWidth = window.innerWidth - e.clientX;
      setRightWidth(Math.max(300, Math.min(900, newWidth)));
    };
    const onMouseUp = () => {
      if (!dragRef.current) return;
      dragRef.current = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    return () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };
  }, []);

  const onResizeStart = useCallback(() => {
    dragRef.current = true;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, []);

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
    setStreamingContent({});

    const abort = submitCodeTask(requirement, {
      onPhase: (data) => {
        if (data.phase === 'start') {
          const match = data.content.match(/\[(.+?)\]/);
          if (match) setTaskId(match[1]);
          addGenMsg('system', 'System', data.content, 'info');
        } else if (data.phase.endsWith('_stream')) {
          // 流式内容 chunk，按阶段累积
          const basePhase = data.phase.replace('_stream', '');
          setStreamingContent(prev => ({
            ...prev,
            [basePhase]: (prev[basePhase] || '') + data.content,
          }));
        } else {
          const node = phaseToNode(data.phase);
          addStep(node, data.content);
          const label = node.charAt(0).toUpperCase() + node.slice(1);
          addGenMsg(node, label, data.content, 'step');
        }
      },
      onResult: (data) => {
        if (data.data) {
          if (data.data.plan) setPlanResult(data.data.plan);
          if (data.data.code) setCodeResult(data.data.code);
          if (data.data.review) setReviewResult(data.data.review);
        }
        addGenMsg('system', 'System', '任务执行完成', 'success');
        setGenStatus('completed');
      },
      onError: (data) => {
        setErrorMessage(data.content);
        addGenMsg('system', 'System', `错误: ${data.content}`, 'error');
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
    setStreamingContent({});
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
    ? (streamingContent.planner || streamingContent.coder || streamingContent.reviewer || planResult || codeResult || reviewResult || errorMessage)
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

      <div
        className="resize-handle"
        onMouseDown={onResizeStart}
      />

      <aside className="right-panel" style={{ width: rightWidth, minWidth: rightWidth }}>
        {/* ===== 生成结果（流式 + 最终结构数据） ===== */}
        {feature === 'generate' && (
          <div className="right-scroll">
            {errorMessage && (
              <div className="error-box">
                <h3>❌ 执行出错</h3>
                <p>{errorMessage}</p>
              </div>
            )}

            {/* Planner 流式内容 */}
            {(streamingContent.planner || planResult) && (
              <div className="right-section">
                <div className="result-section">
                  <h3 className="section-title">📋 任务规划</h3>
                  {streamingContent.planner && (
                    <div className="result-card">
                      <MarkdownRenderer content={streamingContent.planner} />
                    </div>
                  )}
                  {planResult && <PlanResult plan={planResult} />}
                </div>
              </div>
            )}

            {/* Coder 流式内容 */}
            {(streamingContent.coder || codeResult) && (
              <div className="right-section">
                <div className="result-section">
                  <h3 className="section-title">💻 生成代码</h3>
                  {streamingContent.coder && (
                    <div className="result-card">
                      <MarkdownRenderer content={streamingContent.coder} />
                    </div>
                  )}
                  {codeResult && <CodeResult code={codeResult} />}
                </div>
              </div>
            )}

            {/* Reviewer 流式内容 */}
            {(streamingContent.reviewer || reviewResult) && (
              <div className="right-section">
                <div className="result-section">
                  <h3 className="section-title">🔍 代码审查</h3>
                  {streamingContent.reviewer && (
                    <div className="result-card">
                      <MarkdownRenderer content={streamingContent.reviewer} />
                    </div>
                  )}
                  {reviewResult && <ReviewResult review={reviewResult} />}
                </div>
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
