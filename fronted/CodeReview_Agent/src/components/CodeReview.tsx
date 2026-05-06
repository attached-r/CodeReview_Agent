import { useState, useCallback } from 'react';
import type { CodeReviewReport, StreamMessage } from '../types';
import StreamLog from './StreamLog';

const LANGUAGES = [
  { value: 'java', label: 'Java' },
  { value: 'python', label: 'Python' },
  { value: 'javascript', label: 'JavaScript' },
  { value: 'typescript', label: 'TypeScript' },
  { value: 'go', label: 'Go' },
  { value: 'rust', label: 'Rust' },
];

const DEFAULT_CODE = `@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }
}`;

interface Props {
  onStart: () => void;
  onMessage: (msg: StreamMessage) => void;
  onComplete: (report: CodeReviewReport) => void;
  messages: StreamMessage[];
  running: boolean;
}

interface MockStep {
  delay: number;
  node: StreamMessage['node'];
  message: string;
}

function generateMockReview(code: string, language: string): CodeReviewReport {
  const issues = [];
  const securityIssues: string[] = [];
  const styleIssues: string[] = [];

  if (code.includes('@Autowired')) {
    issues.push({ line: 5, severity: 'warning' as const, message: '考虑使用构造器注入替代 @Autowired 字段注入', rule: 'dependency-injection' });
    styleIssues.push('字段注入不推荐，应使用构造器注入');
  }
  if (code.includes('public class') && !code.includes('@Service') && !code.includes('@Repository')) {
    issues.push({ line: 3, severity: 'info' as const, message: 'Controller 应保持精简，业务逻辑应移至 Service 层', rule: 'layered-arch' });
  }
  if (code.includes('orElse(null)')) {
    issues.push({ line: 10, severity: 'error' as const, message: '返回 null 可能引发 NPE，考虑 ResponseEntity.notFound()', rule: 'null-safety' });
    securityIssues.push('直接返回 null 可能导致空指针异常');
  }
  if (code.includes('@RequestBody') && !code.includes('@Valid')) {
    issues.push({ line: 14, severity: 'warning' as const, message: '缺少 @Valid 注解，请求体未进行参数校验', rule: 'validation' });
    styleIssues.push('缺少参数校验注解 @Valid');
  }

  const securityScore = Math.max(0, 100 - securityIssues.length * 15);
  const styleScore = Math.max(0, 100 - styleIssues.length * 10);
  const penalty = issues.filter(i => i.severity === 'error').length * 20 +
    issues.filter(i => i.severity === 'warning').length * 8;
  const score = Math.max(0, Math.min(100, Math.round((securityScore + styleScore) / 2 - penalty)));

  return {
    score,
    summary: `审查完成：发现 ${issues.length} 个问题（${issues.filter(i => i.severity === 'error').length} 个错误，${issues.filter(i => i.severity === 'warning').length} 个警告）`,
    issues,
    suggestions: issues.map(i => i.message),
    securityIssues,
    styleIssues,
  };
}

let mockMsgId = 0;

export default function CodeReview({ onStart, onMessage, onComplete, messages, running }: Props) {
  const [code, setCode] = useState(DEFAULT_CODE);
  const [language, setLanguage] = useState('java');

  const startReview = useCallback(() => {
    onStart();

    const steps: MockStep[] = [
      { delay: 300, node: 'system', message: '开始代码审查流程...' },
      { delay: 500, node: 'planner', message: `解析代码结构，识别语言和框架...` },
      { delay: 800, node: 'planner', message: `检测到 ${LANGUAGES.find(l => l.value === language)?.label} 代码，${code.split('\n').length} 行，${code.length} 字符` },
      { delay: 600, node: 'coder', message: '运行静态分析规则引擎...' },
      { delay: 500, node: 'coder', message: '检查代码规范、安全漏洞、最佳实践...' },
      { delay: 400, node: 'coder', message: '检测依赖注入、空值安全、参数校验...' },
      { delay: 800, node: 'reviewer', message: '汇总分析结果，生成审查报告...' },
      { delay: 400, node: 'system', message: '审查完成，正在生成报告' },
    ];

    const report = generateMockReview(code, language);
    let totalDelay = 0;

    steps.forEach((step, idx) => {
      totalDelay += step.delay;
      setTimeout(() => {
        onMessage({
          id: `rev-${++mockMsgId}`,
          node: step.node,
          label: step.node === 'system' ? 'System' : step.node.charAt(0).toUpperCase() + step.node.slice(1),
          message: step.message,
          timestamp: Date.now(),
          type: idx === steps.length - 1 ? 'success' : 'step',
        });

        if (idx === steps.length - 1) {
          setTimeout(() => onComplete(report), 300);
        }
      }, totalDelay);
    });
  }, [code, language, onStart, onMessage, onComplete]);

  return (
    <>
      <div className="center-section">
        <div className="review-input-card">
          <div className="review-toolbar">
            <label className="input-label">代码内容</label>
            <select
              className="review-lang-select"
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              disabled={running}
            >
              {LANGUAGES.map((l) => (
                <option key={l.value} value={l.value}>{l.label}</option>
              ))}
            </select>
          </div>
          <textarea
            className="review-textarea"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            disabled={running}
            rows={16}
            spellCheck={false}
          />
          <div className="review-actions">
            <span className="input-hint">
              粘贴代码或修改上方示例，提交后将进行静态分析
            </span>
            <button
              className="btn-primary"
              onClick={startReview}
              disabled={running || !code.trim()}
            >
              {running ? '审查中...' : '开始审查'}
            </button>
          </div>
        </div>
      </div>

      <div className="center-section">
        <StreamLog messages={messages} title="审查日志" />
      </div>
    </>
  );
}
