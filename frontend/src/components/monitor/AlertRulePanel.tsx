import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Edit2, Plus, ToggleLeft, ToggleRight, X } from 'lucide-react';
import { monitorApi } from '../../api/monitor';
import type { AlertRule, AlertLog, OperationEventType, PageResponse } from '../../types/monitor';

const EVENT_TYPE_LABELS: Record<OperationEventType, string> = {
  ERROR: '系统错误',
  AUTH: '认证事件',
  AI_SERVICE: 'AI 服务',
  ASYNC_TASK: '异步任务',
};

interface RuleFormData {
  ruleName: string;
  eventType: OperationEventType;
  level: string;
  threshold: number;
  windowMinutes: number;
  notifyChannel: string;
  notifyTarget: string;
  cooldownMinutes: number;
}

const EMPTY_FORM: RuleFormData = {
  ruleName: '',
  eventType: 'ERROR',
  level: '',
  threshold: 5,
  windowMinutes: 10,
  notifyChannel: 'CONSOLE',
  notifyTarget: '',
  cooldownMinutes: 30,
};

export default function AlertRulePanel() {
  const [tab, setTab] = useState<'rules' | 'history'>('rules');
  const [rules, setRules] = useState<AlertRule[]>([]);
  const [history, setHistory] = useState<PageResponse<AlertLog> | null>(null);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<RuleFormData>(EMPTY_FORM);
  const [historyPage, setHistoryPage] = useState(1);

  const fetchRules = useCallback(() => {
    setLoading(true);
    monitorApi
      .getAlertRules()
      .then(setRules)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const fetchHistory = useCallback(() => {
    setLoading(true);
    monitorApi
      .getAlertHistory({ page: historyPage, size: 20 })
      .then(setHistory)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [historyPage]);

  useEffect(() => {
    if (tab === 'rules') fetchRules();
    else fetchHistory();
  }, [tab, fetchRules, fetchHistory]);

  const handleToggle = async (id: number) => {
    try {
      await monitorApi.toggleAlertRule(id);
      fetchRules();
    } catch {}
  };

  const handleSubmit = async () => {
    try {
      if (editingId) {
        await monitorApi.updateAlertRule(editingId, form);
      } else {
        await monitorApi.createAlertRule(form);
      }
      setShowForm(false);
      setEditingId(null);
      setForm(EMPTY_FORM);
      fetchRules();
    } catch {}
  };

  const handleEdit = (rule: AlertRule) => {
    setEditingId(rule.id);
    setForm({
      ruleName: rule.ruleName,
      eventType: rule.eventType,
      level: rule.level || '',
      threshold: rule.threshold,
      windowMinutes: rule.windowMinutes,
      notifyChannel: rule.notifyChannel,
      notifyTarget: rule.notifyTarget || '',
      cooldownMinutes: rule.cooldownMinutes,
    });
    setShowForm(true);
  };

  const formatTime = (dateStr: string | null) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString('zh-CN');
  };

  return (
    <div>
      {/* Tab 切换 */}
      <div className="flex gap-1 mb-4 bg-slate-100 dark:bg-slate-800 rounded-lg p-1 w-fit">
        <button
          onClick={() => setTab('rules')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            tab === 'rules'
              ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm'
              : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'
          }`}
        >
          告警规则
        </button>
        <button
          onClick={() => setTab('history')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            tab === 'history'
              ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm'
              : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'
          }`}
        >
          告警历史
        </button>
      </div>

      {/* 规则列表 */}
      {tab === 'rules' && (
        <div>
          <div className="flex justify-end mb-4">
            <button
              onClick={() => {
                setEditingId(null);
                setForm(EMPTY_FORM);
                setShowForm(true);
              }}
              className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 text-sm"
            >
              <Plus className="w-4 h-4" />
              新建规则
            </button>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-40">
              <div className="w-8 h-8 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
            </div>
          ) : rules.length === 0 ? (
            <div className="text-center py-12 text-slate-400">暂无告警规则</div>
          ) : (
            <div className="space-y-3">
              {rules.map((rule) => (
                <motion.div
                  key={rule.id}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="flex items-center justify-between p-4 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-slate-800 dark:text-white">
                        {rule.ruleName}
                      </span>
                      <span className="px-2 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400">
                        {EVENT_TYPE_LABELS[rule.eventType]}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      阈值: {rule.threshold}次 / {rule.windowMinutes}分钟 | 温却: {rule.cooldownMinutes}分钟 | 渠道: {rule.notifyChannel}
                    </p>
                    {rule.lastTriggeredAt && (
                      <p className="text-xs text-amber-500 mt-1">
                        上次触发: {formatTime(rule.lastTriggeredAt)}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2 ml-4">
                    <button
                      onClick={() => handleEdit(rule)}
                      className="p-2 text-slate-400 hover:text-primary-500 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleToggle(rule.id)}
                      className={`p-2 rounded-lg ${
                        rule.enabled
                          ? 'text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/20'
                          : 'text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700'
                      }`}
                    >
                      {rule.enabled ? (
                        <ToggleRight className="w-5 h-5" />
                      ) : (
                        <ToggleLeft className="w-5 h-5" />
                      )}
                    </button>
                  </div>
                </motion.div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 告警历史 */}
      {tab === 'history' && (
        <div>
          {loading ? (
            <div className="flex items-center justify-center h-40">
              <div className="w-8 h-8 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
            </div>
          ) : !history || history.content.length === 0 ? (
            <div className="text-center py-12 text-slate-400">暂无告警历史</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-700">
                    <th className="text-left py-3 px-3 text-slate-500 font-medium">规则名称</th>
                    <th className="text-left py-3 px-3 text-slate-500 font-medium">触发次数</th>
                    <th className="text-left py-3 px-3 text-slate-500 font-medium">触发时间</th>
                    <th className="text-left py-3 px-3 text-slate-500 font-medium">状态</th>
                  </tr>
                </thead>
                <tbody>
                  {history.content.map((log) => (
                    <tr
                      key={log.id}
                      className="border-b border-slate-100 dark:border-slate-800"
                    >
                      <td className="py-3 px-3 text-slate-700 dark:text-slate-300">
                        {log.ruleName}
                      </td>
                      <td className="py-3 px-3">
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400">
                          {log.eventCount}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-slate-600 dark:text-slate-400">
                        {formatTime(log.triggeredAt)}
                      </td>
                      <td className="py-3 px-3">
                        {log.resolved ? (
                          <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">
                            已恢复
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">
                            告警中
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {history && history.totalPages > 1 && (
            <div className="flex items-center justify-between mt-4">
              <span className="text-sm text-slate-500">
                共 {history.totalElements} 条
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setHistoryPage((p) => Math.max(1, p - 1))}
                  disabled={historyPage === 1}
                  className="px-3 py-1.5 rounded-lg text-sm border border-slate-200 dark:border-slate-600 disabled:opacity-50"
                >
                  上一页
                </button>
                <button
                  onClick={() => setHistoryPage((p) => Math.min(history.totalPages, p + 1))}
                  disabled={historyPage === history.totalPages}
                  className="px-3 py-1.5 rounded-lg text-sm border border-slate-200 dark:border-slate-600 disabled:opacity-50"
                >
                  下一页
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 规则表单弹窗 */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="bg-white dark:bg-slate-800 rounded-2xl shadow-xl w-full max-w-md mx-4 p-6"
          >
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-slate-800 dark:text-white">
                {editingId ? '编辑规则' : '新建规则'}
              </h3>
              <button
                onClick={() => {
                  setShowForm(false);
                  setEditingId(null);
                }}
                className="text-slate-400 hover:text-slate-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                  规则名称
                </label>
                <input
                  type="text"
                  value={form.ruleName}
                  onChange={(e) => setForm({ ...form, ruleName: e.target.value })}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                    事件类型
                  </label>
                  <select
                    value={form.eventType}
                    onChange={(e) =>
                      setForm({ ...form, eventType: e.target.value as OperationEventType })
                    }
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                  >
                    {Object.entries(EVENT_TYPE_LABELS).map(([key, label]) => (
                      <option key={key} value={key}>{label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                    日志级别
                  </label>
                  <select
                    value={form.level}
                    onChange={(e) => setForm({ ...form, level: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                  >
                    <option value="">全部</option>
                    <option value="ERROR">ERROR</option>
                    <option value="WARN">WARN</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                    阈值（次）
                  </label>
                  <input
                    type="number"
                    min={1}
                    value={form.threshold}
                    onChange={(e) =>
                      setForm({ ...form, threshold: parseInt(e.target.value) || 1 })
                    }
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                    时间窗口（分钟）
                  </label>
                  <input
                    type="number"
                    min={1}
                    value={form.windowMinutes}
                    onChange={(e) =>
                      setForm({ ...form, windowMinutes: parseInt(e.target.value) || 1 })
                    }
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                    通知渠道
                  </label>
                  <select
                    value={form.notifyChannel}
                    onChange={(e) => setForm({ ...form, notifyChannel: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                  >
                    <option value="CONSOLE">控制台</option>
                    <option value="WEBHOOK">Webhook</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                    冷却期（分钟）
                  </label>
                  <input
                    type="number"
                    min={1}
                    value={form.cooldownMinutes}
                    onChange={(e) =>
                      setForm({ ...form, cooldownMinutes: parseInt(e.target.value) || 30 })
                    }
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                  />
                </div>
              </div>

              {form.notifyChannel === 'WEBHOOK' && (
                <div>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                    Webhook URL
                  </label>
                  <input
                    type="url"
                    value={form.notifyTarget}
                    onChange={(e) => setForm({ ...form, notifyTarget: e.target.value })}
                    placeholder="https://oapi.dingtalk.com/robot/send?access_token=..."
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm"
                  />
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => {
                  setShowForm(false);
                  setEditingId(null);
                }}
                className="px-4 py-2 rounded-lg text-sm border border-slate-200 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-700"
              >
                取消
              </button>
              <button
                onClick={handleSubmit}
                className="px-4 py-2 rounded-lg text-sm bg-primary-500 text-white hover:bg-primary-600"
              >
                {editingId ? '保存' : '创建'}
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
}
