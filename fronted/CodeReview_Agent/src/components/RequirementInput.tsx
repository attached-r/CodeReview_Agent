import { useState, type FormEvent } from 'react';

interface Props {
  onSubmit: (requirement: string) => void;
  disabled: boolean;
}

export default function RequirementInput({ onSubmit, disabled }: Props) {
  const [text, setText] = useState('');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (text.trim() && !disabled) {
      onSubmit(text.trim());
    }
  };

  return (
    <form className="requirement-input" onSubmit={handleSubmit}>
      <label htmlFor="req-input" className="input-label">
        输入需求描述
      </label>
      <textarea
        id="req-input"
        className="input-textarea"
        placeholder="例如：生成一个 Spring Boot RESTful API，包含用户注册、登录功能，使用 JPA + MySQL..."
        rows={4}
        value={text}
        onChange={(e) => setText(e.target.value)}
        disabled={disabled}
      />
      <div className="input-actions">
        <span className="input-hint">
          描述你希望生成的代码功能，Agent 将自动完成规划、编码和审查
        </span>
        <button
          type="submit"
          className="btn-primary"
          disabled={disabled || !text.trim()}
        >
          {disabled ? '执行中...' : '开始生成'}
        </button>
      </div>
    </form>
  );
}
