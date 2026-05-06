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
public class CoderOutput {

    private List<CodeFile> codeFiles;
    private String explanation;
    private List<String> dependencies;
    private String uncertaintyNote;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeFile {
        private String filePath;
        private String language;
        private String content;
        private String description;
    }
}


