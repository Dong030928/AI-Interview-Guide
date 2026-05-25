package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.monitor")
public class MonitorProperties {

  private Notification notification = new Notification();

  @Data
  public static class Notification {
    private boolean enabled = true;
    private String webhookUrl = "";
  }
}
