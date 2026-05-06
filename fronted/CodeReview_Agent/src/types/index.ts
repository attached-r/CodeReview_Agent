/* ===== Backend Model Types ===== */

export interface SubTask {
  name: string;
  description: string;
  targetFile: string;
}

export interface PlannerOutput {
  taskAnalysis: string;
  subTasks: SubTask[];
  techStack: string;
  riskWarning: string;
}

export interface CodeFile {
  filePath: string;
  language: string;
  content: string;
  description: string;
}

export interface CoderOutput {
  codeFiles: CodeFile[];
  explanation: string;
  dependencies: string[];
  uncertaintyNote: string;
}

export interface ReviewOutput {
  accepted: boolean;
  issues: string[];
  hallucinationIssues: string[];
  fixSuggestions: string[];
  score: number;
  summary: string;
}

/* ===== SSE Event Types ===== */

export type SSEEventName = 'START' | 'AGENT_EVENT' | 'COMPLETED' | 'ERROR';

export interface SSEStartEvent {
  taskId: string;
  node: string;
  message: string;
  requirement: string;
}

export interface SSEAgentEvent {
  node: string;
  target: string;
  message: string;
}

export interface SSECompletedEvent {
  accepted: boolean;
  plan: PlannerOutput | null;
  code: CoderOutput | null;
  review: ReviewOutput | null;
}

export interface SSEErrorEvent {
  message: string;
}

export type AgentNode = 'planner' | 'coder' | 'reviewer' | 'end';

export interface AgentStep {
  node: AgentNode;
  message: string;
  timestamp: number;
}

export type TaskStatus = 'idle' | 'running' | 'completed' | 'error';

/* ===== Stream Message (for real-time log) ===== */

export interface StreamMessage {
  id: string;
  node: AgentNode | 'system';
  label: string;
  message: string;
  timestamp: number;
  type: 'step' | 'info' | 'error' | 'success';
}

/* ===== Feature Type ===== */

export type FeatureType = 'generate' | 'review';

/* ===== Code Review (mock) ===== */

export interface CodeReviewRequest {
  code: string;
  language: string;
}

export interface CodeReviewIssue {
  line: number;
  severity: 'error' | 'warning' | 'info';
  message: string;
  rule: string;
}

export interface CodeReviewReport {
  score: number;
  summary: string;
  issues: CodeReviewIssue[];
  suggestions: string[];
  securityIssues: string[];
  styleIssues: string[];
}
