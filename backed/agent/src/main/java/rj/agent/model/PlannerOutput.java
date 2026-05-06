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
public class PlannerOutput {

    private String taskAnalysis;
    private List<SubTask> subTasks;
    private String techStack;
    private String riskWarning;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTask {
        private String name;
        private String description;
        private String targetFile;
    }
}
