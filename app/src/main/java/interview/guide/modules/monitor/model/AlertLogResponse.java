package interview.guide.modules.monitor.model;

import java.time.LocalDateTime;

public record AlertLogResponse(
    Long id,
    Long ruleId,
    String ruleName,
    int eventCount,
    LocalDateTime triggeredAt,
    boolean resolved,
    LocalDateTime resolvedAt
) {
  public static AlertLogResponse from(SysAlertLogEntity entity) {
    return new AlertLogResponse(
        entity.getId(),
        entity.getRuleId(),
        entity.getRuleName(),
        entity.getEventCount(),
        entity.getTriggeredAt(),
        entity.getResolved(),
        entity.getResolvedAt());
  }
}
