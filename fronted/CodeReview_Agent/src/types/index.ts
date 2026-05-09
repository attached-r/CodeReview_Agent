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

/* ===== SSE Event Types (匹配后端 StreamEvent 模型) ===== */

export type SSEEventName = 'phase' | 'result' | 'error';

export interface SSEPhaseEvent {
  type: 'phase';
  phase: string;
  content: string;
}

export interface SSEResultEvent {
  type: 'result';
  phase: string;
  content: string;
  data: {
    accepted: boolean;
    plan: PlannerOutput | null;
    code: CoderOutput | null;
    review: ReviewOutput | null;
    retryCount: number;
  } | null;
}

export interface SSEErrorEvent {
  type: 'error';
  phase: string;
  content: string;
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

/* ===== ConversationMessage (matches backend entity) ===== */

export interface ConversationMessage {
  id: number;
  conversationId: string;
  messageId: string;
  role: string;
  content: string;
  tokenCount: number | null;
  modelName: string | null;
  metadata: Record<string, unknown> | null;
  messageIndex: number | null;
  parentMessageId: string | null;
  createdAt: string;
}

/* ===== Conversation (matches backend entity) ===== */

export interface Conversation {
  id: number;
  conversationId: string;
  userId: number;
  title: string;
  requirement: string;
  status: 'ACTIVE' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}
