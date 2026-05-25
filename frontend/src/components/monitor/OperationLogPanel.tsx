import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, ChevronRight, Search, X } from 'lucide-react';
import { monitorApi } from '../../api/monitor';
import type { OperationLog, OperationEventType, PageResponse } from '../../types/monitor';

const EVENT_TYPE_LABELS: Record<OperationEventType, string> = {
  ERROR: '系统错误',
  AUTH: '认证事件',
  AI_SERVICE: 'AI 服务',
  ASYNC_TASK: '异步任务',
};

const EVENT_TYPE_COLORS: Record<OperationEventType, string> = {
  ERROR: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  AUTH: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  AI_SERVICE: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  ASYNC_TASK: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
};

const LEVEL_COLORS: Record<string, string> = {
  ERROR: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  WARN: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  INFO: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
};

export default function OperationLogPanel() {
  const [logs, setLogs] = useState<PageResponse<OperationLog> | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [eventType, setEventType] = useState<OperationEventType | ''>('');
  const [level, setLevel] = useState('');
  const [keyword, setKeyword] = useState('');
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const fetchLogs = useCallback(() => {
    setLoading(true);
    monitorApi
      .getOperationLogs({
        page,
        size: 20,
        eventType: eventType || undefined,
        level: level || undefined,
        keyword: keyword || undefined,
      })
      .then(setLogs)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [page, eventType, level, keyword]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const formatTime = (dateStr: string) => {
    return new Date(dateStr).toLocaleString('zh-CN');
  };

  return (
    <div>
      {/* 筛选栏 */}
      <div className="flex flex-wrap gap-3 mb-4">
        <select
          value={eventType}
          onChange={(e) => {
            setEventType(e.target.value as OperationEventType | '');
            setPage(1);
          }}
          className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-300"
        >
          <option value="">全部事件类型</option>
          {Object.entries(EVENT_TYPE_LABELS).map(([key, label]) => (
            <option key={key} value={key}>{label}</option>
          ))}
        </select>

        <select
          value={level}
          onChange={(e) => {
            setLevel(e.target.value);
            setPage(1);
          }}
          className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-300"
        >
          <option value="">全部级别</option>
          <option value="ERROR">ERROR</option>
          <option value="WARN">WARN</option>
          <option value="INFO">INFO</option>
        </select>

        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && fetchLogs()}
            placeholder="搜索消息关键词..."
            className="w-full pl-9 pr-8 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-slate-700 dark:text-slate-300 placeholder-slate-400"
          />
          {keyword && (
            <button
              onClick={() => {
                setKeyword('');
                setPage(1);
              }}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* 表格 */}
      {loading ? (
        <div className="flex items-center justify-center h-40">
          <div className="w-8 h-8 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
        </div>
      ) : !logs || logs.content.length === 0 ? (
        <div className="text-center py-12 text-slate-400">暂无日志记录</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-700">
                <th className="text-left py-3 px-3 text-slate-500 font-medium w-8" />
                <th className="text-left py-3 px-3 text-slate-500 font-medium">时间</th>
                <th className="text-left py-3 px-3 text-slate-500 font-medium">级别</th>
                <th className="text-left py-3 px-3 text-slate-500 font-medium">事件类型</th>
                <th className="text-left py-3 px-3 text-slate-500 font-medium">来源</th>
                <th className="text-left py-3 px-3 text-slate-500 font-medium">消息</th>
              </tr>
            </thead>
            <tbody>
              {logs.content.map((log) => (
                <motion.tr
                  key={log.id}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="border-b border-slate-100 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50"
                >
                  <td className="py-3 px-3">
                    <button
                      onClick={() =>
                        setExpandedId(expandedId === log.id ? null : log.id)
                      }
                      className="text-slate-400 hover:text-slate-600"
                    >
                      {expandedId === log.id ? (
                        <ChevronDown className="w-4 h-4" />
                      ) : (
                        <ChevronRight className="w-4 h-4" />
                      )}
                    </button>
                  </td>
                  <td className="py-3 px-3 text-slate-600 dark:text-slate-400 whitespace-nowrap">
                    {formatTime(log.createdAt)}
                  </td>
                  <td className="py-3 px-3">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs font-medium ${LEVEL_COLORS[log.level] || 'bg-slate-100 text-slate-600'}`}
                    >
                      {log.level}
                    </span>
                  </td>
                  <td className="py-3 px-3">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs font-medium ${EVENT_TYPE_COLORS[log.eventType]}`}
                    >
                      {EVENT_TYPE_LABELS[log.eventType]}
                    </span>
                  </td>
                  <td className="py-3 px-3 text-slate-600 dark:text-slate-400 max-w-[150px] truncate">
                    {log.source}
                  </td>
                  <td className="py-3 px-3 text-slate-700 dark:text-slate-300 max-w-[300px] truncate">
                    {log.message}
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>

          {/* 展开详情 */}
          <AnimatePresence>
            {logs.content.map(
              (log) =>
                expandedId === log.id && (
                  <motion.div
                    key={`detail-${log.id}`}
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    className="overflow-hidden bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-700"
                  >
                    <div className="p-4 space-y-2 text-xs">
                      {log.traceId && (
                        <div>
                          <span className="text-slate-500">TraceId: </span>
                          <code className="text-slate-700 dark:text-slate-300">{log.traceId}</code>
                        </div>
                      )}
                      {log.ipAddress && (
                        <div>
                          <span className="text-slate-500">IP: </span>
                          <span className="text-slate-700 dark:text-slate-300">{log.ipAddress}</span>
                        </div>
                      )}
                      {log.userId && (
                        <div>
                          <span className="text-slate-500">UserId: </span>
                          <span className="text-slate-700 dark:text-slate-300">{log.userId}</span>
                        </div>
                      )}
                      {log.metadata && (
                        <div>
                          <span className="text-slate-500">Metadata: </span>
                          <code className="text-slate-700 dark:text-slate-300 break-all">
                            {log.metadata}
                          </code>
                        </div>
                      )}
                      {log.stackTrace && (
                        <div>
                          <span className="text-slate-500">StackTrace: </span>
                          <pre className="mt-1 p-2 bg-slate-100 dark:bg-slate-900 rounded text-xs overflow-x-auto text-red-600 dark:text-red-400 max-h-48 overflow-y-auto">
                            {log.stackTrace}
                          </pre>
                        </div>
                      )}
                    </div>
                  </motion.div>
                ),
            )}
          </AnimatePresence>
        </div>
      )}

      {/* 分页 */}
      {logs && logs.totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-sm text-slate-500">
            共 {logs.totalElements} 条，第 {logs.number + 1}/{logs.totalPages} 页
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
              className="px-3 py-1.5 rounded-lg text-sm border border-slate-200 dark:border-slate-600 disabled:opacity-50 hover:bg-slate-50 dark:hover:bg-slate-800"
            >
              上一页
            </button>
            <button
              onClick={() => setPage((p) => Math.min(logs.totalPages, p + 1))}
              disabled={page === logs.totalPages}
              className="px-3 py-1.5 rounded-lg text-sm border border-slate-200 dark:border-slate-600 disabled:opacity-50 hover:bg-slate-50 dark:hover:bg-slate-800"
            >
              下一页
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
