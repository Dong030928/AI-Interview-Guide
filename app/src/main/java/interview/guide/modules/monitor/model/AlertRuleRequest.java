package interview.guide.modules.monitor.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertRuleRequest(
    @NotBlank String ruleName,
    @NotNull OperationEventType eventType,
    String level,
    @Min(1) int threshold,
    @Min(1) int windowMinutes,
    @NotBlank String notifyChannel,
    String notifyTarget,
    @Min(1) int cooldownMinutes
) {}
