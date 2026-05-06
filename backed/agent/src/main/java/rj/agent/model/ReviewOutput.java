package rj.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewOutput {

    private boolean accepted;
    private List<String> issues;
    private List<String> hallucinationIssues;
    private List<String> fixSuggestions;
    private int score;
    private String summary;
}
