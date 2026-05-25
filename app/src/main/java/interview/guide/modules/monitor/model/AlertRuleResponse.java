package interview.guide.modules.monitor.model;

import java.time.LocalDateTime;

public record AlertRuleResponse(
    Long id,
    String ruleName,
    OperationEventType eventType,
    String level,
    int threshold,
    int windowMinutes,
    boolean enabled,
    String notifyChannel,
    String notifyTarget,
    int cooldownMinutes,
    LocalDateTime lastTriggeredAt,
    LocalDateTime createdAt
) {
  public static AlertRuleResponse from(SysAlertRuleEntity entity) {
    return new AlertRuleResponse(
        entity.getId(),
        entity.getRuleName(),
        entity.getEventType(),
        entity.getLevel(),
        entity.getThreshold(),
        entity.getWindowMinutes(),
        entity.getEnabled(),
        entity.getNotifyChannel(),
        entity.getNotifyTarget(),
        entity.getCooldownMinutes(),
        entity.getLastTriggeredAt(),
        entity.getCreatedAt());
  }
}
