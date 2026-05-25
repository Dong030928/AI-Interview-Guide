package interview.guide.modules.monitor.service;

import interview.guide.modules.monitor.notification.AlertEvent;
import interview.guide.modules.monitor.notification.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationService {

  private final Map<String, NotificationChannel> channels;

  public NotificationService(List<NotificationChannel> channelList) {
    this.channels = channelList.stream()
        .collect(Collectors.toMap(NotificationChannel::channelName, Function.identity()));
  }

  public void notify(AlertEvent alert, String channelName, String target) {
    NotificationChannel channel = channels.get(channelName);
    if (channel == null) {
      log.warn("未知通知渠道: {}, 回退到控制台", channelName);
      channel = channels.get("CONSOLE");
    }

    if (channel != null) {
      try {
        channel.send(alert, target);
      } catch (Exception e) {
        log.warn("通知发送失败: channel={}, error={}", channelName, e.getMessage());
      }
    }
  }
}
