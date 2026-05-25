package interview.guide.modules.monitor.notification;

import interview.guide.modules.monitor.model.OperationEventType;

import java.time.LocalDateTime;

public record AlertEvent(
    String ruleName,
    OperationEventType eventType,
    int eventCount,
    int windowMinutes,
    int threshold,
    LocalDateTime triggeredAt
) {}
