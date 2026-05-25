package interview.guide;

import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Interview Platform - Main Application
 * 智能AI面试官平台 - 主启动类
 */
@EnableScheduling
@SpringBootApplication(exclude = {
    OpenAiAudioSpeechAutoConfiguration.class,
    OpenAiAudioTranscriptionAutoConfiguration.class,
    OpenAiChatAutoConfiguration.class,
    OpenAiEmbeddingAutoConfiguration.class,
    OpenAiImageAutoConfiguration.class,
    OpenAiModerationAutoConfiguration.class
})
public class App {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(App.class, args);

        String apiKey = ctx.getEnvironment().getProperty("app.ai.providers.dashscope.api-key");
        String model = ctx.getEnvironment().getProperty("app.ai.providers.dashscope.model");
        String baseUrl = ctx.getEnvironment().getProperty("app.ai.providers.dashscope.base-url");
        String webhookUrl = ctx.getEnvironment().getProperty("app.monitor.notification.webhook-url");

        System.out.println("dashscope apiKey = " +
                (apiKey == null ? "null" : apiKey.substring(0, Math.min(8, apiKey.length())) + "****"));
        System.out.println("dashscope model = " + model);
        System.out.println("dashscope baseUrl = " + baseUrl);
        System.out.println("DingDing robot webhook url = " + webhookUrl);
    }
}
