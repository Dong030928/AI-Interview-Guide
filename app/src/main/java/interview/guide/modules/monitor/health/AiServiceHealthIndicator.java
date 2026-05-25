package interview.guide.modules.monitor.health;

import interview.guide.common.ai.LlmProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiServiceHealthIndicator implements HealthIndicator {

  private final LlmProviderRegistry llmProviderRegistry;

  @Override
  public Health health() {
    try {
      ChatClient client = llmProviderRegistry.getChatClientOrDefault(null);
      if (client == null) {
        return Health.down().withDetail("reason", "No default ChatClient available").build();
      }
      // 简单 prompt 验证连通性
      String response = client.prompt()
          .user("ping")
          .call()
          .content();
      if (response != null && !response.isBlank()) {
        return Health.up().withDetail("provider", "default").build();
      }
      return Health.down().withDetail("reason", "Empty response from AI service").build();
    } catch (Exception e) {
      log.warn("AI service health check failed: {}", e.getMessage());
      return Health.down().withException(e).build();
    }
  }
}
