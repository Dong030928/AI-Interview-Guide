package interview.guide.modules.monitor.service;

import interview.guide.modules.monitor.model.SysAlertLogEntity;
import interview.guide.modules.monitor.model.SysAlertRuleEntity;
import interview.guide.modules.monitor.repository.SysAlertLogRepository;
import interview.guide.modules.monitor.repository.SysAlertRuleRepository;
import interview.guide.modules.monitor.repository.SysOperationLogRepository;
import interview.guide.modules.monitor.notification.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluationService {

  private final SysAlertRuleRepository alertRuleRepository;
  private final SysAlertLogRepository alertLogRepository;
  private final SysOperationLogRepository operationLogRepository;
  private final NotificationService notificationService;

  @Scheduled(fixedRate = 60_000)
  public void evaluateAlerts() {
    List<SysAlertRuleEntity> rules = alertRuleRepository.findAllByEnabledTrue();
    if (rules.isEmpty()) {
      return;
    }

    for (SysAlertRuleEntity rule : rules) {
      try {
        evaluateRule(rule);
      } catch (Exception e) {
        log.warn("告警规则评估失败: ruleId={}, error={}", rule.getId(), e.getMessage());
      }
    }
  }

  private void evaluateRule(SysAlertRuleEntity rule) {
    // 检查冷却期
    if (rule.getLastTriggeredAt() != null) {
      LocalDateTime cooldownEnd =
          rule.getLastTriggeredAt().plusMinutes(rule.getCooldownMinutes());
      if (LocalDateTime.now().isBefore(cooldownEnd)) {
        return;
      }
    }

    LocalDateTime windowStart = LocalDateTime.now().minusMinutes(rule.getWindowMinutes());

    long count;
    if (rule.getLevel() != null && !rule.getLevel().isBlank()) {
      count = operationLogRepository.countByEventTypeAndCreatedAtAfter(
          rule.getEventType(), windowStart);
      // 进一步按 level 过滤需要自定义查询，这里简化为按 eventType 统计
    } else {
      count = operationLogRepository.countByEventTypeAndCreatedAtAfter(
          rule.getEventType(), windowStart);
    }

    if (count >= rule.getThreshold()) {
      triggerAlert(rule, (int) count);
    }
  }

  private void triggerAlert(SysAlertRuleEntity rule, int eventCount) {
    // 记录告警日志
    SysAlertLogEntity alertLog = SysAlertLogEntity.builder()
        .ruleId(rule.getId())
        .ruleName(rule.getRuleName())
        .eventCount(eventCount)
        .triggeredAt(LocalDateTime.now())
        .resolved(false)
        .build();
    alertLogRepository.save(alertLog);

    // 更新规则触发时间
    rule.setLastTriggeredAt(LocalDateTime.now());
    alertRuleRepository.save(rule);

    // 发送通知
    AlertEvent alertEvent = new AlertEvent(
        rule.getRuleName(),
        rule.getEventType(),
        eventCount,
        rule.getWindowMinutes(),
        rule.getThreshold(),
        LocalDateTime.now());

    notificationService.notify(alertEvent, rule.getNotifyChannel(), rule.getNotifyTarget());

    log.warn("告警触发: rule={}, events={}/{} in {}min",
        rule.getRuleName(), eventCount, rule.getThreshold(), rule.getWindowMinutes());
  }
}
