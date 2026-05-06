import type { ReviewOutput } from '../types';

interface Props {
  review: ReviewOutput;
}

function scoreColor(score: number): string {
  if (score >= 90) return 'score-excellent';
  if (score >= 70) return 'score-good';
  if (score >= 60) return 'score-fair';
  return 'score-poor';
}

export default function ReviewResult({ review }: Props) {
  return (
    <div className="result-section">
      <h3 className="section-title">🔍 代码审查</h3>

      <div className="result-card">
        <div className="review-header">
          <div className={`review-score ${scoreColor(review.score)}`}>
            <span className="score-value">{review.score}</span>
            <span className="score-label">/ 100</span>
          </div>
          <div className="review-verdict">
            {review.accepted ? (
              <span className="verdict-pass">✅ 审查通过</span>
            ) : (
              <span className="verdict-fail">❌ 需要修复</span>
            )}
          </div>
        </div>
        {review.summary && (
          <p className="result-text review-summary">{review.summary}</p>
        )}
      </div>

      {review.issues && review.issues.length > 0 && (
        <div className="result-card">
          <h4>发现的问题 ({review.issues.length})</h4>
          <ul className="issue-list">
            {review.issues.map((issue, i) => (
              <li key={i} className="issue-item issue-item-normal">
                {issue}
              </li>
            ))}
          </ul>
        </div>
      )}

      {review.hallucinationIssues && review.hallucinationIssues.length > 0 && (
        <div className="result-card result-card-error">
          <h4>🚫 幻觉问题 ({review.hallucinationIssues.length})</h4>
          <ul className="issue-list">
            {review.hallucinationIssues.map((issue, i) => (
              <li key={i} className="issue-item issue-item-hallucination">
                {issue}
              </li>
            ))}
          </ul>
        </div>
      )}

      {review.fixSuggestions && review.fixSuggestions.length > 0 && (
        <div className="result-card">
          <h4>修复建议</h4>
          <ul className="issue-list">
            {review.fixSuggestions.map((s, i) => (
              <li key={i} className="issue-item issue-item-suggestion">
                💡 {s}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
