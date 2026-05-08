package rj.agent.config;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 模型配置
 * 对三个agent进行模型配置
 */
@Configuration
public class ModelConfig {
    //自定义 ollama api 传入Rest Client 增加超时设置
    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)  // 设置超时时间为30秒
            .responseTimeout(Duration.ofMinutes(10));  // 设置响应超时为10分钟

        ReactorClientHttpRequestFactory requestFactory = new ReactorClientHttpRequestFactory(httpClient);

        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
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
