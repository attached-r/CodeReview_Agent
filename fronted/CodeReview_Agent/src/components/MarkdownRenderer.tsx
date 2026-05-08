import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Components } from 'react-markdown';

interface Props {
  content: string;
}

const components: Components = {
  code: ({ className, children, ...props }) => {
    const isInline = !className;
    if (isInline) {
      return <code className="md-inline-code" {...props}>{children}</code>;
    }
    return (
      <pre className="md-code-block">
        <code className={className} {...props}>{children}</code>
      </pre>
    );
  },
  a: ({ href, children }) => (
    <a className="md-link" href={href} target="_blank" rel="noreferrer">{children}</a>
  ),
  table: ({ children }) => (
    <div className="md-table-wrap">
      <table className="md-table">{children}</table>
    </div>
  ),
  blockquote: ({ children }) => (
    <blockquote className="md-blockquote">{children}</blockquote>
  ),
};

export default function MarkdownRenderer({ content }: Props) {
  if (!content) return null;

  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={components}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
