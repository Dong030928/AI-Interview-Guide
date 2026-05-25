package interview.guide.modules.monitor.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class ConsoleNotificationChannel implements NotificationChannel {

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void send(AlertEvent alert, String target) {
    log.warn("[ALERT] 规则[{}]触发: {}事件在{}分钟内发生{}次(阈值{}), 触发时间: {}",
        alert.ruleName(),
        alert.eventType(),
        alert.windowMinutes(),
        alert.eventCount(),
        alert.threshold(),
        alert.triggeredAt().format(FMT));
  }

  @Override
  public String channelName() {
    return "CONSOLE";
  }
}
