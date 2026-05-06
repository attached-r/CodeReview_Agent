import type { AgentNode, AgentStep, TaskStatus } from '../types';

interface Props {
  steps: AgentStep[];
  status: TaskStatus;
}

interface NodeMeta {
  id: AgentNode;
  label: string;
  icon: string;
}

const NODES: NodeMeta[] = [
  { id: 'planner', label: 'Planner', icon: '📋' },
  { id: 'coder', label: 'Coder', icon: '💻' },
  { id: 'reviewer', label: 'Reviewer', icon: '🔍' },
  { id: 'end', label: '完成', icon: '✅' },
];

/** 根据最新事件判断当前活跃节点 */
function getActiveNode(steps: AgentStep[]): AgentNode | null {
  if (steps.length === 0) return null;
  const last = steps[steps.length - 1];
  if (last.node === 'end') return 'end';

  // 检查该节点是否已推进到下一个阶段
  const nodeOrder: AgentNode[] = ['planner', 'coder', 'reviewer'];
  const idx = nodeOrder.indexOf(last.node);
  if (idx === -1) return null;

  // 如果当前节点有后续步骤，则当前节点已完成
  for (let i = idx + 1; i < nodeOrder.length; i++) {
    if (steps.some((s) => s.node === nodeOrder[i])) {
      return null; // 已推进到更后面的阶段
    }
  }
  return last.node;
}

function getNodeStatus(
  nodeId: AgentNode,
  steps: AgentStep[],
  activeNode: AgentNode | null,
  taskStatus: TaskStatus,
): 'pending' | 'active' | 'done' | 'error' {
  if (taskStatus === 'error' && activeNode === nodeId) return 'error';
  if (nodeId === 'end') {
    return taskStatus === 'completed' ? 'done' : 'pending';
  }

  const hasStep = steps.some((s) => s.node === nodeId);

  if (nodeId === activeNode) return 'active';
  if (hasStep && nodeId !== activeNode) return 'done';

  // If a later node has started, earlier nodes are done
  const nodeOrder: AgentNode[] = ['planner', 'coder', 'reviewer'];
  const currentIdx = nodeOrder.indexOf(nodeId);
  for (let i = currentIdx + 1; i < nodeOrder.length; i++) {
    if (steps.some((s) => s.node === nodeOrder[i])) {
      return 'done';
    }
  }

  return activeNode ? 'pending' : 'pending';
}

const lastMessageForNode = (steps: AgentStep[], node: AgentNode): string | undefined => {
  const nodeSteps = [...steps].reverse();
  for (const step of nodeSteps) {
    if (step.node === node) return step.message;
  }
  return undefined;
};

export default function AgentFlow({ steps, status }: Props) {
  const activeNode = getActiveNode(steps);

  return (
    <div className="agent-flow">
      <h3 className="section-title">Agent 执行流程</h3>
      <div className="flow-track">
        {NODES.map((node, idx) => {
          const nodeStatus = getNodeStatus(node.id, steps, activeNode, status);
          const msg = lastMessageForNode(steps, node.id);

          return (
            <div
              key={node.id}
              className={`flow-node flow-node-${nodeStatus}`}
            >
              <div className="flow-node-row">
                <span className="flow-icon">{node.icon}</span>
                <div className="flow-info">
                  <span className="flow-label">{node.label}</span>
                  {msg && <span className="flow-msg">{msg}</span>}
                </div>
                <span className="flow-indicator">
                  {nodeStatus === 'active' && <span className="spinner" />}
                  {nodeStatus === 'done' && '✓'}
                  {nodeStatus === 'error' && '✗'}
                  {nodeStatus === 'pending' && idx + 1}
                </span>
              </div>
              {idx < NODES.length - 1 && (
                <div className={`flow-connector flow-connector-${nodeStatus}`} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
