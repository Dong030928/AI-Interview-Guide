package interview.guide.modules.monitor.model;

import java.time.LocalDateTime;

public record OperationLogQueryRequest(
    int page,
    int size,
    OperationEventType eventType,
    String level,
    LocalDateTime startDate,
    LocalDateTime endDate,
    String keyword
) {
  public OperationLogQueryRequest {
    if (page < 1) page = 1;
    if (size < 1) size = 20;
    if (size > 100) size = 100;
  }
}
