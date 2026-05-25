package interview.guide.modules.monitor.model;

import java.time.LocalDateTime;

public record OperationLogResponse(
    Long id,
    OperationEventType eventType,
    String level,
    String source,
    String message,
    String stackTrace,
    Long userId,
    String ipAddress,
    String traceId,
    String metadata,
    LocalDateTime createdAt
) {
  public static OperationLogResponse from(SysOperationLogEntity entity) {
    return new OperationLogResponse(
        entity.getId(),
        entity.getEventType(),
        entity.getLevel(),
        entity.getSource(),
        entity.getMessage(),
        entity.getStackTrace(),
        entity.getUserId(),
        entity.getIpAddress(),
        entity.getTraceId(),
        entity.getMetadata(),
        entity.getCreatedAt());
  }
}
