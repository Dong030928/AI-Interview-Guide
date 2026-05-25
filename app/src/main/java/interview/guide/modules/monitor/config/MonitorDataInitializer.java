package interview.guide.modules.monitor.config;

import interview.guide.modules.monitor.model.OperationEventType;
import interview.guide.modules.monitor.model.SysAlertRuleEntity;
import interview.guide.modules.monitor.repository.SysAlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorDataInitializer implements CommandLineRunner {

  private final SysAlertRuleRepository alertRuleRepository;

  @Override
  public void run(String... args) {
    createDefaultRule("AI 服务异常频繁", OperationEventType.AI_SERVICE, null, 5, 10);
    createDefaultRule("登录失败过多", OperationEventType.AUTH, "WARN", 10, 5);
    createDefaultRule("异步任务失败", OperationEventType.ASYNC_TASK, null, 3, 10);
    createDefaultRule("系统错误频发", OperationEventType.ERROR, "ERROR", 10, 5);
  }

  private void createDefaultRule(
      String name, OperationEventType eventType, String level, int threshold, int windowMinutes) {
    if (alertRuleRepository.existsByRuleName(name)) {
      return;
    }
    SysAlertRuleEntity rule = SysAlertRuleEntity.builder()
        .ruleName(name)
        .eventType(eventType)
        .level(level)
        .threshold(threshold)
        .windowMinutes(windowMinutes)
        .enabled(true)
        .notifyChannel("CONSOLE")
        .cooldownMinutes(30)
        .build();
    alertRuleRepository.save(rule);
    log.info("初始化告警规则: {}", name);
  }
}
