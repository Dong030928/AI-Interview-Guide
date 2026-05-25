export type OperationEventType = 'ERROR' | 'AUTH' | 'AI_SERVICE' | 'ASYNC_TASK';

export interface OperationLog {
  id: number;
  eventType: OperationEventType;
  level: string;
  source: string;
  message: string;
  stackTrace: string | null;
  userId: number | null;
  ipAddress: string | null;
  traceId: string | null;
  metadata: string | null;
  createdAt: string;
}

export interface AlertRule {
  id: number;
  ruleName: string;
  eventType: OperationEventType;
  level: string | null;
  threshold: number;
  windowMinutes: number;
  enabled: boolean;
  notifyChannel: string;
  notifyTarget: string | null;
  cooldownMinutes: number;
  lastTriggeredAt: string | null;
  createdAt: string;
}

export interface AlertLog {
  id: number;
  ruleId: number;
  ruleName: string;
  eventCount: number;
  triggeredAt: string;
  resolved: boolean;
  resolvedAt: string | null;
}

export interface MonitorStats {
  totalLogsToday: number;
  errorCountToday: number;
  alertCountToday: number;
  activeRuleCount: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
