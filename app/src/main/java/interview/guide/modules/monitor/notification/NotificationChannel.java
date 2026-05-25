package interview.guide.modules.monitor.notification;

public interface NotificationChannel {

  void send(AlertEvent alert, String target);

  String channelName();
}
