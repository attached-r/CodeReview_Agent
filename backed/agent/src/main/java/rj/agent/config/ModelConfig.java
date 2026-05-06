package rj.agent.config;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// ... existing code ...

/**
 * 模型配置
 * 对三个agent进行模型配置 自动读取配置文件
 * 后续可以添加其他模型配置
 */
@Configuration
public class ModelConfig {

    @Bean
    public OllamaApi ollamaApi() {
        return new OllamaApi.Builder().build();
    }

    @Primary
    @Bean("plannerChatModel")
    public ChatModel plannerChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model("qwen2.5-coder:7b")
                        .temperature(0.1)
                        .topP(0.2)
                        .build())
                .build();
    }

    @Bean("coderChatModel")
    public ChatModel coderChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model("qwen2.5-coder:7b")
                        .temperature(0.2)
                        .topP(0.3)
                        .build())
                .build();
    }

    @Bean("reviewerChatModel")
    public ChatModel reviewerChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model("qwen2.5-coder:7b")
                        .temperature(0.1)
                        .topP(0.2)
                        .build())
                .build();
    }
}
