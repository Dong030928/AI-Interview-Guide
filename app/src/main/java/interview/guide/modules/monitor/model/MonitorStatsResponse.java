package interview.guide.modules.monitor.model;

public record MonitorStatsResponse(
    long totalLogsToday,
    long errorCountToday,
    long alertCountToday,
    long activeRuleCount
) {}
