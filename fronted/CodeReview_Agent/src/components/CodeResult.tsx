import { useState } from 'react';
import type { CoderOutput, CodeFile } from '../types';

interface Props {
  code: CoderOutput;
}

export default function CodeResult({ code }: Props) {
  const [activeFile, setActiveFile] = useState(0);

  const files = code.codeFiles ?? [];
  const active: CodeFile | undefined = files[activeFile];

  return (
    <div className="result-section">
      <h3 className="section-title">💻 生成代码</h3>

      {code.explanation && (
        <div className="result-card">
          <p className="result-text">{code.explanation}</p>
        </div>
      )}

      {files.length > 0 && (
        <>
          <div className="code-tabs">
            {files.map((f, i) => (
              <button
                key={i}
                className={`code-tab ${i === activeFile ? 'code-tab-active' : ''}`}
                onClick={() => setActiveFile(i)}
              >
                {f.filePath.split('/').pop()}
              </button>
            ))}
          </div>

          {active && (
            <div className="code-viewer">
              <div className="code-header">
                <span className="code-path">{active.filePath}</span>
                <span className="code-lang">{active.language}</span>
              </div>
              <pre className="code-content">
                <code>{active.content}</code>
              </pre>
            </div>
          )}
        </>
      )}

      {code.dependencies && code.dependencies.length > 0 && (
        <div className="result-card">
          <h4>依赖声明</h4>
          <ul className="dep-list">
            {code.dependencies.map((dep, i) => (
              <li key={i}>
                <code>{dep}</code>
              </li>
            ))}
          </ul>
        </div>
      )}

      {code.uncertaintyNote && (
        <div className="result-card result-card-warn">
          <h4>⚠️ 不确定性说明</h4>
          <p className="result-text">{code.uncertaintyNote}</p>
        </div>
      )}
    </div>
  );
}
