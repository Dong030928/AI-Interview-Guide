import { request } from './request';
import type {
  AlertLog,
  AlertRule,
  MonitorStats,
  OperationLog,
  OperationEventType,
  PageResponse,
} from '../types/monitor';

export const monitorApi = {
  getOperationLogs(params: {
    page?: number;
    size?: number;
    eventType?: OperationEventType;
    level?: string;
    startDate?: string;
    endDate?: string;
    keyword?: string;
  }): Promise<PageResponse<OperationLog>> {
    return request.get('/api/monitor/logs', { params });
  },

  getStats(): Promise<MonitorStats> {
    return request.get('/api/monitor/stats');
  },

  getAlertRules(): Promise<AlertRule[]> {
    return request.get('/api/monitor/alerts/rules');
  },

  createAlertRule(rule: {
    ruleName: string;
    eventType: OperationEventType;
    level?: string;
    threshold: number;
    windowMinutes: number;
    notifyChannel: string;
    notifyTarget?: string;
    cooldownMinutes: number;
  }): Promise<AlertRule> {
    return request.post('/api/monitor/alerts/rules', rule);
  },

  updateAlertRule(
    id: number,
    rule: {
      ruleName: string;
      eventType: OperationEventType;
      level?: string;
      threshold: number;
      windowMinutes: number;
      notifyChannel: string;
      notifyTarget?: string;
      cooldownMinutes: number;
    },
  ): Promise<AlertRule> {
    return request.put(`/api/monitor/alerts/rules/${id}`, rule);
  },

  toggleAlertRule(id: number): Promise<AlertRule> {
    return request.patch(`/api/monitor/alerts/rules/${id}/toggle`);
  },

  getAlertHistory(params?: {
    page?: number;
    size?: number;
    startDate?: string;
    endDate?: string;
  }): Promise<PageResponse<AlertLog>> {
    return request.get('/api/monitor/alerts/history', { params });
  },
};
