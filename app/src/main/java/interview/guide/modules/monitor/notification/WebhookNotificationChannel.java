package interview.guide.modules.monitor.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@Slf4j
public class WebhookNotificationChannel implements NotificationChannel {

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final RestTemplate restTemplate;

  public WebhookNotificationChannel(RestTemplateBuilder builder) {
    this.restTemplate = builder
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(5))
        .build();
  }

  @Override
  public void send(AlertEvent alert, String target) {
    if (target == null || target.isBlank()) {
      log.warn("Webhook URL 未配置，跳过通知");
      return;
    }

    try {
      String content = String.format(
          "[告警] 规则名: %s\n事件类型: %s\n触发次数: %d\n时间窗口: %d分钟\n阈值: %d\n触发时间: %s",
          alert.ruleName(),
          alert.eventType(),
          alert.eventCount(),
          alert.windowMinutes(),
          alert.threshold(),
          alert.triggeredAt().format(FMT));

      // 兼容钉钉/飞书机器人格式
      Map<String, Object> payload = Map.of(
          "msgtype", "text",
          "text", Map.of("content", content));

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      ResponseEntity<String> response = restTemplate.postForEntity(
          target, new HttpEntity<>(payload, headers), String.class);

      // 钉钉/飞书返回 JSON：{"errcode":0,"errmsg":"ok"}，errcode!=0 表示失败
      String body = response.getBody();
      if (response.getStatusCode().is2xxSuccessful() && body != null && body.contains("\"errcode\":0")) {
        log.info("Webhook 告警通知已发送: rule={}, response={}", alert.ruleName(), body);
      } else {
        log.warn("Webhook 告警通知发送被拒绝: rule={}, status={}, body={}",
            alert.ruleName(), response.getStatusCode(), body);
      }
    } catch (Exception e) {
      log.warn("Webhook 告警通知发送失败: rule={}, error={}", alert.ruleName(), e.getMessage());
    }
  }

  @Override
  public String channelName() {
    return "WEBHOOK";
  }
}
